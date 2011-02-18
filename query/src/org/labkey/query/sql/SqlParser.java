/*
 * Copyright (c) 2006-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.query.sql;

import antlr.TokenStreamRecognitionException;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.MissingTokenException;
import org.antlr.runtime.ParserRuleReturnScope;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeAdaptor;
import org.antlr.runtime.tree.Tree;
import org.apache.log4j.Category;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.query.sql.antlr.SqlBaseLexer;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.labkey.query.sql.antlr.SqlBaseParser.*;


/**
 * SqlParser is responsible for the first two phases of the SQL transformation process
 *
 * step one - the ANTLR parser returns a tree of Nodes
 * step two - translate the tree into a tree of QNodes
 * 
 */

@SuppressWarnings({"ThrowableResultOfMethodCallIgnored","ThrowableInstanceNeverThrown"})
public class SqlParser
{
	private static Category _log = Category.getInstance(SqlParser.class);

    ArrayList<Exception> _parseErrors;
    QNode _root;
    ArrayList<QParameter> _parameters;
    

    //
    // PUBLIC
    //

    public SqlParser()
    {
    }


    // for testing only
    public Tree rawQuery(String str) throws Exception
    {
        _parseErrors = new ArrayList<Exception>();
        _SqlParser parser = new _SqlParser(str, _parseErrors);
        ParserRuleReturnScope selectScope = null;
        selectScope = parser.statement();
        if (!_parseErrors.isEmpty())
            throw _parseErrors.get(0);
        return (Tree)selectScope.getTree();
    }


	public QNode parseQuery(String str, List<? super QueryParseException> errors)
	{
		_parseErrors = new ArrayList<Exception>();
		try
		{
 			_SqlParser parser = new _SqlParser(str, _parseErrors);
            ParserRuleReturnScope selectScope = null;
			try
			{
				selectScope = parser.statement();
                int last = parser.getTokenStream().LA(1);
				if (EOF != last)
                {
                    CommonToken t = (CommonToken)parser.getTokenStream().LT(1);
					//noinspection ThrowableInstanceNeverThrown
                    if (null != t)
                        _parseErrors.add(new QueryParseException("Unexpected token: " + t.getText(), null, t.getLine(), t.getCharPositionInLine()));
                    else
					    _parseErrors.add(new QueryParseException("EOF expected", null, 0, 0));
                }
			}
			catch (Exception x)
			{
				_parseErrors.add(x);
			}

			if (_parseErrors.size() == 0)
			{
				CommonTree parseRoot = (CommonTree) selectScope.getTree();
				assert parseRoot != null;
                assert parseRoot.getType() == STATEMENT;
                assert parseRoot.getChildCount()==1 || parseRoot.getChildCount()==2;
//                assert dump(parseRoot);

                CommonTree parameters = null;
                if (parseRoot.getChildCount() == 2)
                {
                    _parameters = new ArrayList<QParameter>();
                    parameters = (CommonTree)parseRoot.getChild(0);
                    for (Object parameter : parameters.getChildren())
                    {
                        QParameter p = convertParameter((CommonTree)parameter);
                        if (null == p)
                        {
                            assert !_parseErrors.isEmpty();
                            continue;
                        }
                        _parameters.add(p);
                    }
                }
                CommonTree selectStmt = (CommonTree)parseRoot.getChild(parseRoot.getChildCount()-1);
                if (selectStmt.getType() != QUERY && selectStmt.getType() != UNION && selectStmt.getType() != UNION_ALL)
                {
                    _parseErrors.add(new QueryParseException(tokenName(selectStmt.getType()) + " statements are not supported", null, parseRoot.getLine(), parseRoot.getCharPositionInLine()));
                    return null;
                }

				QNode qnodeRoot = convertParseTree(selectStmt);
//				assert dump(qnodeRoot);
				assert MemTracker.put(qnodeRoot);

				if (qnodeRoot instanceof QQuery || qnodeRoot instanceof QUnion)
					_root = qnodeRoot;
				else
					errors.add(new QueryParseException("This does not look like a SELECT or UNION query", null, 0, 0));
            }
			
			for (Throwable e : _parseErrors)
			{
				errors.add(wrapParseException(e));
			}

			return _root;
		}
		catch (Exception e)
		{
			errors.add(wrapParseException(e));
			return null;
		}
	}


    public QNode getRoot()
    {
        return _root;
    }


    public ArrayList<QParameter> getParameters()
    {
        return null==_parameters ? new ArrayList<QParameter>(0) : _parameters;
    }

	
    public QExpr parseExpr(String str, List<? super QueryParseException> errors)
    {
        _parseErrors = new ArrayList<Exception>();
        try
        {
            _SqlParser parser = new _SqlParser(str, _parseErrors);
            ParserRuleReturnScope exprScope = null;
            try
            {
                exprScope = parser.expression();
                int last = parser.getTokenStream().LA(1);
                if (EOF != last)
                    //noinspection ThrowableInstanceNeverThrown
                    _parseErrors.add(new QueryParseException("EOF expected", null, 0, 0));
            }
            catch (Exception x)
            {
                _parseErrors.add(x);
            }
            if (_parseErrors.size() != 0)
                return null;

            CommonTree parseRoot = (CommonTree)exprScope.getTree();
            assert MemTracker.put(parseRoot);
            if (null == parseRoot)
                return null;

            QNode qnodeRoot = convertParseTree(parseRoot);
			assert dump(qnodeRoot);
            assert MemTracker.put(qnodeRoot);

            _root = qnodeRoot != null && qnodeRoot instanceof QExpr ? (QExpr) qnodeRoot : null;
            for (Throwable e : _parseErrors)
            {
                errors.add(wrapParseException(e));
            }
            return (QExpr)_root;
        }
        catch (Exception e)
        {
            errors.add(wrapParseException(e));
            return null;
        }
    }


    public static String toPrefixString(Tree tree)
    {
        StringBuilder sb = new StringBuilder();
        _prefix(tree, sb);
        return sb.toString();
    }


    private static void _prefix(Tree tree, StringBuilder sb)
    {
        if (tree.getChildCount() == 0)
            sb.append(tree.getText());
        else
        {
            sb.append("(");
            sb.append(tree.getText());
            for (int i=0 ; i<tree.getChildCount() ; i++)
            {
                sb.append(" ");
                _prefix(tree.getChild(i),sb);
            }
            sb.append(")");
        }
    }

    public static String toPrefixString(QNode tree)
    {
        StringBuilder sb = new StringBuilder();
        _prefix(tree, sb);
        return sb.toString();
    }


    private static void _prefix(QNode tree, StringBuilder sb)
    {
        if (tree.getFirstChild() == null)
            sb.append(_text(tree));
        else
        {
            sb.append("(");
            sb.append(_text(tree));
            for (QNode child : tree.children())
            {
                sb.append(" ");
                _prefix(child,sb);
            }
            sb.append(")");
        }
    }

    private static String _text(QNode q)
    {
        if (q.getTokenType() == METHOD_CALL)
            return "METHOD_CALL";
        return q.getTokenText();
    }


    public boolean dump(CommonTree tree)
    {
        if (!_log.isDebugEnabled())
            return true;
        StringWriter sw = new StringWriter();
        dump(tree, new PrintWriter(sw), "\n");
        _log.debug(sw.toString());
        return true;
    }


    public void dump(CommonTree tree, PrintWriter out)
    {
        dump(tree, out, "\n");
        out.println();
    }


    protected void dump(Tree tree, PrintWriter out, String nl)
    {
        out.printf("%s%s: %s", nl, getClass().getSimpleName(), tree.getText());
        for (int i=0 ; i<tree.getChildCount() ; i++)
        {
            Tree c = tree.getChild(i);
            dump(c, out, nl + "    |");
        }
    }




	private boolean dump(QNode node)
	{
		if (null != node && _log.isDebugEnabled())
		{
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			node.dump(pw);
			pw.close();
			_log.debug(sw.toString());
		}
		return true;
	}

    static public boolean isLegalIdentifierChar(char ch, boolean fFirst)
    {
        if (!fFirst && ch >= '0' && ch <= '9')
            return true;
        return ch == '_' || ch == '$' ||
                ch >= 'a' && ch <= 'z' ||
                ch >= 'A' && ch <= 'Z';
    }

    static public boolean isLegalIdentifier(String str)
    {
        if (str.length() == 0)
            return false;
        if (keywords.contains(str))
            return false;
        for (int i = 0; i < str.length(); i ++)
        {
            if (!isLegalIdentifierChar(str.charAt(i), i == 0))
            {
                return false;
            }
        }
        return true;
    }

    static public String makeLegalIdentifier(String str)
    {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < str.length(); i ++)
        {
            char ch = str.charAt(i);
            if (isLegalIdentifierChar(ch, i == 0))
            {
                ret.append(ch);
            }
            else
            {
                ret.append('_');
            }
        }
        return ret.toString();
    }
    

    //
    // IMPL
    //

    static private Set<String> keywords = new CaseInsensitiveHashSet(PageFlowUtil.set(
            "all","any","and","as","asc","avg",
            "between","both",
            "case","class","count",
            "delete","desc","distinct",
            "elements","else","empty","end","escape","exists",
            "false","fetch","from","full",
            "group",
            "having",
            "in","indices","inner","insert","into","is",
            "join",
            "leading","left","like","limit",
            "max","member","min",
            "new","not","null",
            "of","on","or","order","outer",
            "right",
            "select","set","some","stddev","sum",
            "trailing","then","true",
            "union","update","user",
            "versioned",
            "when","where"
            ));


    static QueryParseException wrapParseException(Throwable e)
    {
        if (e instanceof QueryParseException)
        {
            return (QueryParseException) e;
        }
        if (e instanceof TokenStreamRecognitionException)
        {
            e = ((TokenStreamRecognitionException) e).recog;
        }
        if (e instanceof RecognitionException)
        {
            RecognitionException re = (RecognitionException)e;
            String message = formatRecognitionException(re);
            return new QueryParseException(message, re, re.line, re.charPositionInLine);
        }
        return new QueryParseException("Unexpected exception", e, 0, 0);
    }


    static String formatRecognitionException(RecognitionException re)
    {
        String message = re.getMessage();
        if (null != message)
            return message;

        String missing = null;
        String near = null;
        
        if (null != re.token)
            near = re.token.getText();
        if (re instanceof MissingTokenException)
        {
            MissingTokenException mte = (MissingTokenException)re;
            if (null != mte.inserted)
            missing = tokenName(((CommonToken)mte.inserted).getType());
        }

        if (null != near)
            message = "Syntax error near '" + near + "'";
        else
            message = "Syntax error";
        if (null != missing)
            message += ", expected '" + missing + "'";
        return message;
    }


    public static String tokenName(int type)
    {
        switch (type)
        {
            case EOF: return "EOF";
            case AGGREGATE: return "AGGREGATE FUNCTION";
            case ALIAS: return "AS";
            case EXPR_LIST: return "EXPR LIST";
            case IN_LIST: return "IN LIST";
            case IS_NOT: return "IS NOT";
            case METHOD_CALL: return "METHOD CALL";
            case NOT_BETWEEN: return "NOT BETWEEN";
            case NOT_IN: return "NOT IN";
            case NOT_LIKE: return "NOT LIKE";
            case QUERY: return "QUERY";
            case RANGE: return "RANGE";
            case ROW_STAR: return "*";
            case SELECT_FROM: return "SELECT FROM";
            case UNARY_MINUS: return "-";
            case UNARY_PLUS: return "+";
            case UNION_ALL: return "UNION ALL";
            case ALL: return "ALL";
            case ANY: return "ANY";
            case AND: return "AND";
            case AS: return "AS";
            case ASCENDING: return "ASCENDING";
            case AVG: return "AVG";
            case BETWEEN: return "BETWEEN";
            case CASE: return "CASE";
            case CAST: return "CAST";
            case COUNT: return "COUNT";
            case DATATYPE: return "DATATYPE";
            case DELETE: return "DELETE";
            case DESCENDING: return "DESCENDING";
            case DISTINCT: return "DISTINCT";
            case DOT: return "DOT";
            case ELSE: return "ELSE";
            case END: return "END";
            case ESCAPE: return "ESCAPE";
            case EXISTS: return "EXISTS";
            case FALSE: return "FALSE";
            case FROM: return "FROM";
            case FULL: return "FULL";
            case GROUP: return "GROUP";
            case HAVING: return "HAVING";
            case IN: return "IN";
            case INNER: return "INNER";
            case INSERT: return "INSERT";
            case INTO: return "INTO";
            case IS: return "IS";
            case JOIN: return "JOIN";
            case LEFT: return "LEFT";
            case LIKE: return "LIKE";
            case LIMIT: return "LIMIT";
            case MAX: return "MAX";
            case GROUP_CONCAT: return "GROUP_CONCAT";
            case MIN: return "MIN";
            case NOT: return "NOT";
            case NULL: return "NULL";
            case ON: return "ON";
            case OR: return "OR";
            case ORDER: return "ORDER";
            case OUTER: return "OUTER";
            case RIGHT: return "RIGHT";
            case SELECT: return "SELECT";
            case SET: return "SET";
            case SOME: return "SOME";
            case STDDEV: return "STDDEV";
            case SUM: return "SUM";
            case THEN: return "THEN";
            case TRUE: return "TRUE";
            case UNION: return "UNION";
            case UPDATE: return "UPDATE";
            case WHERE: return "WHERE";
            case WHEN: return "WHEN";
            case COMMA: return ",";
            case EQ: return "=";
            case OPEN: return "(";
            case CLOSE: return ")";
            case NUM_INT: return "NUMBER";
            case BIT_OR: return "|";
            case BIT_XOR: return "^";
            case NE: return "!=";
            case SQL_NE: return "<>";
            case LT: return "<";
            case GT: return ">";
            case LE: return "<=";
            case GE: return ">=";
            case CONCAT: return "||";
            case PLUS: return "+";
            case MINUS: return "-";
            case BIT_AND: return "&";
            case STAR: return "*";
            case DIV: return "/";
            case PARAM: return "?";
            case QUOTED_STRING: return "QUOTED STRING";
            case NUM_LONG: return "NUMBER";
            case NUM_DOUBLE: return "NUMBER";
            case NUM_FLOAT: return "NUMBER";
            case IDENT: return "IDENTIFIER";
            case QUOTED_IDENTIFIER: return "QUOTED IDENTIFIER";
            case COLON: return ":";
            case ID_START_LETTER: return "ID_START_LETTER";
            case ID_LETTER: return "ID_LETTER";
            case WS: return "WHITE SPACE";
            case EXPONENT: return "EXPONENT";
            case FLOAT_SUFFIX: return "FLOAT_SUFFIX";
            case HEX_DIGIT: return "HEX_DIGIT";
            case COMMENT: return "COMMENT";
            case LINE_COMMENT: return "LINE COMMENT";
        }
        return null;
    }


    private QParameter convertParameter(CommonTree node)
    {
        if (node.getChildCount() < 2 || node.getChildCount() > 3)
        {
            _parseErrors.add(new QueryParseException("Invalid parameter declaration", null, node.getLine(), node.getCharPositionInLine()));
            return null;
        }

        QNode parameter = convertParseTree(node);
        QNode nodeName = parameter.childList().get(0);
        QNode nodeType = parameter.childList().get(1);
        QNode nodeDefault = null;
        
        if (parameter.childList().size() > 2)
        {
            nodeDefault = parameter.childList().get(2);
            if (!(nodeDefault instanceof IConstant))
            {
                Tree n = node.getChild(2);
                _parseErrors.add(new QueryParseException("Constant expected after DEFAULT", null, n.getLine(), n.getCharPositionInLine()));
                return null;
            }
        }

        if (!(nodeName instanceof QIdentifier) || !(nodeType instanceof QIdentifier) || (null != nodeDefault && !(nodeDefault instanceof QExpr)))
        {
            _parseErrors.add(new QueryParseException("Parse exception in parameter declaration", null, node.getLine(), node.getCharPositionInLine()));
            return null;
        }

        QIdentifier identName = (QIdentifier)nodeName;
        QIdentifier identType = (QIdentifier)nodeType;
        QExpr exprDefault = (QExpr)nodeDefault;
        boolean required = exprDefault == null;
        ParameterType type = ParameterType.valueOf(identType.getIdentifier());
        Object value = null==exprDefault ? null : type.convert(((IConstant)exprDefault).getValue());

        if (null == type)
        {
            _parseErrors.add(new QueryParseException("Type is not supported: " + identType.getIdentifier(), null, identType.getLine(), identType.getColumn()));
            return null;
        }
        return new QParameter(parameter, identName.getIdentifier(), type, required, value);
    }
    

	private QNode convertParseTree(CommonTree node)
	{
		LinkedList<QNode> l = new LinkedList<QNode>();
		for (int i=0 ; i<node.getChildCount() ; i++)
		{
            CommonTree child = (CommonTree)node.getChild(i);
			QNode q = convertParseTree(child);
			if (q != null)
				l.add(q);
			else
				assert _parseErrors.size() > 0;
		}
		return convertNode(node, l);
	}

	
	private QNode convertNode(CommonTree node, LinkedList<QNode> children)
	{
		switch (node.getType())
		{
            case ALIAS:
            case AS:
            {
                if (children.size() == 1)
                    return first(children);
                node.getToken().setType(AS);
                break;
            }
			case METHOD_CALL:
            {
                QNode id = first(children), exprList = second(children);
                if (!(id instanceof QIdentifier))
                        break;
                String name = ((QIdentifier)id).getIdentifier().toLowerCase();

                if (name.equals("convert") || name.equals("cast"))
                {
                    if (!(exprList instanceof QExprList) || exprList.childList().size() != 2)
                    {
                        _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 2 arguments", null, node.getLine(), node.getCharPositionInLine()));
                        break;
                    }
                    LinkedList<QNode> args = new LinkedList<QNode>();
                    args.add(exprList.childList().get(0));
                    args.add(constantToStringNode(exprList.childList().get(1)));
                    exprList._replaceChildren(args);
                    validateConvertConstant(args.get(1));
                }
                else if (name.equals("timestampadd") || name.equals("timestampdiff"))
                {
                    if (!(exprList instanceof QExprList) || exprList.childList().size() != 3)
                    {
                        _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 3 arguments", null, node.getLine(), node.getCharPositionInLine()));
                        break;
                    }
                    assert exprList.childList().size() == 3;
                    LinkedList<QNode> args = new LinkedList<QNode>();
                    args.add(constantToStringNode(exprList.childList().get(0)));
                    args.add(exprList.childList().get(1));
                    args.add(exprList.childList().get(2));
                    exprList._replaceChildren(args);
                    validateTimestampConstant(args.get(0));
                }
                else if (name.equals("age"))
                {
                    if (!(exprList instanceof QExprList) || exprList.childList().size() < 2 || exprList.childList().size() > 3)
                    {
                        _parseErrors.add(new QueryParseException(name.toUpperCase() + " function expects 2 or 3 arguments", null, node.getLine(), node.getCharPositionInLine()));
                        break;
                    }
                    assert exprList.childList().size() == 2 || exprList.childList().size() == 3;
                    LinkedList<QNode> args = new LinkedList<QNode>();
                    args.add(exprList.childList().get(0));
                    args.add(exprList.childList().get(1));
                    if (exprList.childList().size() == 3)
                        args.add(constantToStringNode(exprList.childList().get(2)));
                    exprList._replaceChildren(args);
                    if (args.size() == 3)
                        validateTimestampConstant(args.get(2));
                }
                
                try
                {
                    Method m = Method.valueOf(name);
                    if (null != m)
                    {
                        m.validate(node, exprList.childList(), _parseErrors);
                    }
                }
                catch (IllegalArgumentException x)
                {
                }
                
				break;
            }
            case AGGREGATE:
            {
                if ((QAggregate.GROUP_CONCAT.equalsIgnoreCase(node.getText()) || QAggregate.COUNT.equalsIgnoreCase(node.getText()))
                        && children.size() > 1 && first(children) instanceof QDistinct)
                {
                    children.remove(0);
                    QAggregate result = (QAggregate)qnode(node, children);
                    result.setDistinct(true);
                    return result;
                }
                break;
            }
			default:
				break;
		}

		return qnode(node, children);
	}


    private boolean validateConvertConstant(QNode n)
    {
        if (!(n instanceof QString))
        {
            _parseErrors.add(new QueryParseException("constant expected", null, n.getLine(), n.getColumn()));
            return false;
        }
        String s = ((QString)n).getValue();
        if (s.startsWith("SQL_"))
            s = s.substring(4);
        try
        {
            ConvertType.valueOf(s);
            return true;
        }
        catch (IllegalArgumentException x)
        {
            _parseErrors.add(new QueryParseException("Unrecognized constant '" + ((QString)n).getValue() + "'", null, n.getLine(), n.getColumn()));
            return false;
        }
    }


    private boolean validateTimestampConstant(QNode n)
    {
        if (!(n instanceof QString))
        {
            _parseErrors.add(new QueryParseException("constant expected", null, n.getLine(), n.getColumn()));
            return false;
        }
        String s = ((QString)n).getValue();
        if (!s.startsWith("SQL_TSI_"))
            s = "SQL_TSI_" + s;
        try
        {
            Method.TimestampDiffInterval.valueOf(s);
            return true;
        }
        catch (IllegalArgumentException x)
        {
            _parseErrors.add(new QueryParseException("Unrecognized constant '" + ((QString)n).getValue() + "'", null, n.getLine(), n.getColumn()));
            return false;
        }
    }


	private static QNode first(LinkedList<QNode> children)
	{
		return children.size() > 0 ? children.get(0) : null;
	}


	private static QNode second(LinkedList<QNode> children)
	{
		return children.size() > 1 ? children.get(1) : null;
	}
	

	private QNode constantToStringNode(QNode node)
	{
        if (node instanceof QString)
        {
            QString q = new QString();
            q.setTokenText(node.getTokenText().toUpperCase());
            q.setLineAndColumn(node);
            return q;
        }
        else if (node instanceof QIdentifier)
        {
		    String s =  ((QIdentifier)node).getIdentifier();
		    QString q = new QString(s.toUpperCase());
		    q.setLineAndColumn(node);
		    return q;
        }
        else
            return node;
	}


    public static class CaseInsensitiveStringStream extends ANTLRStringStream
    {
        public CaseInsensitiveStringStream(String s)
        {
            super(s);
        }

        @Override
        public int LA(int i)
        {
            int r = super.LA(i);
            return 'A' <= r && r <= 'Z' ? r + ('a'-'A') : r;
        }
    }


    private static class AdaptorWrapper extends CommonTreeAdaptor
    {
        @Override
        public Object errorNode(TokenStream input, Token start, Token stop, RecognitionException e)
        {
            return super.errorNode(input, start, stop, e);
        }
    }
    

    private static class _SqlParser extends SqlBaseParser
	{
        final ArrayList<Exception> _errors;
        
		public _SqlParser(String str, ArrayList<Exception> errors)
		{
			super(new CommonTokenStream(new SqlBaseLexer(new CaseInsensitiveStringStream(str))));
            setTreeAdaptor(new AdaptorWrapper());
            _errors = errors;
            assert MemTracker.put(this);
        }

        public boolean isSqlType(String type)
        {
            type = type.toUpperCase();
            if (type.startsWith("SQL_"))
                type = type.substring(4);
            try
            {
                ConvertType.valueOf(type);
                return true;
            }
            catch (IllegalArgumentException x)
            {
                return false;
            }
        }

		@Override
		public void reportError(RecognitionException ex)
		{
			_errors.add(ex);
		}
	}


	QNode qnode(CommonTree n, LinkedList<QNode> children)
	{
		QNode q = qnode(n);
		if (q != null)
			q._replaceChildren(children);
		return q;
	}



	QNode qnode(CommonTree node)
    {
		int type = node.getType();
		QNode q = null;
		
        switch (type)
        {
            case AS:
                q = new QAs();
				break;
            case IDENT:
            case QUOTED_IDENTIFIER:
                q = new QIdentifier();
				break;
            case DOT:
                q = new QDot();
				break;
            case QUOTED_STRING:
                q = new QString();
				break;
            case TRUE:
            case FALSE:
                q = new QBoolean();
				break;
            case NUM_DOUBLE:
            case NUM_FLOAT:
            case NUM_INT:
            case NUM_LONG:
                return new QNumber(node);
            case FROM:
                q = new QFrom();
				break;
            case SELECT_FROM:
                q = new QSelectFrom();
				break;
            case SELECT:
                q = new QSelect();
				break;
            case PIVOT:
                q = new QPivot();
                break;
            case QUERY:
                q = new QQuery();
				break;
            case WHERE:
                q = new QWhere();
				break;
            case HAVING:
                q = new QWhere(true);
                break;
            case METHOD_CALL:
                q = new QMethodCall();
				break;
            case AGGREGATE:
            case COUNT:
                q = new QAggregate();
				break;
            case EXPR_LIST:
            case IN_LIST:
                q = new QExprList();
				break;
            case ROW_STAR:
				q = new QRowStar();
				break;
            case GROUP:
                q = new QGroupBy();
				break;
            case ORDER:
                q = new QOrder();
				break;
            case CASE:
                q = new QCase();
				break;
            case WHEN:
                q = new QWhen();
				break;
            case ELSE:
                q = new QElse();
				break;
            case NULL:
                q = new QNull();
				break;
            case LIMIT:
                q = new QLimit();
				break;
            case DISTINCT:
                q = new QDistinct();
				break;
			case UNION:
            case UNION_ALL:
				return new QUnion(node);

			case ON:
			case INNER:
			case LEFT:
			case RIGHT:
			case OUTER:
			case JOIN:
			case FULL:
            case CROSS:
			case ASCENDING:
			case DESCENDING:
			case RANGE:
				return new QUnknownNode(node);

            case EQ: case NE: case GT: case LT: case GE: case LE: case IS: case IS_NOT:
            case BETWEEN: case NOT_BETWEEN:
            case PLUS: case MINUS: case UNARY_MINUS: case STAR: case DIV: case CONCAT:
            case NOT: case AND: case OR: case LIKE: case NOT_LIKE: case IN: case NOT_IN:
            case BIT_AND: case BIT_OR: case BIT_XOR: case UNARY_PLUS:
                Operator op = Operator.ofTokenType(type);
				assert op != null;
                if (op == null)
				{
					_parseErrors.add(new QueryParseException("Unexpected token '" + node.getText() + "'", null, node.getLine(), node.getCharPositionInLine()));
			    	return null;
				}
				q = op.expr();
				break;
			case EXISTS:
			case ANY:
			case SOME:
			case ALL:
				_parseErrors.add(new QueryParseException("EXISTS,ANY,ALL, and SOME are not supported", null, node.getLine(), node.getCharPositionInLine()));
				 return null;
			case ESCAPE:
				_parseErrors.add(new QueryParseException("LIKE ESCAPE is not supported", null, node.getLine(), node.getCharPositionInLine()));
				 return null;
            case DECLARATION:
                return new QUnknownNode();
			default:
	            _parseErrors.add(new QueryParseException("Unexpected token '" + node.getText() + "'", null, node.getLine(), node.getCharPositionInLine()));
				return null;
        }

		assert q != null || _parseErrors.size() > 0;
		
		// default behavior for nodes that don't have QNode(Node N) constructors
		if (q != null)
			q.from(node);
		return q;
    }




    //
    // TESTS
    //


	
    /* UNDONE keywords
    class delete elements fetch indices insert into limit new set update versioned both empty leading member of trailing
 	*/
    static String[] testSql = new String[]
    {
        "SELECT 'text',1,-2,1000000L,1.0f,3.1415926535897932384626433832795,6.02214179e23,TRUE,FALSE,0x0ab12,NULL FROM R",

        "SELECT DISTINCT R.a, b AS B FROM rel R INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",
        "SELECT R.* FROM R",

        "SELECT \"a\",\"b\",AVG(x),COUNT(x),COUNT(*),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b ORDER BY a ASC, b DESC, SUM(x)",

        "SELECT a = TRUE, b = FALSE, NOT c FROM R WHERE R.x IN (2,3,5,7) OR R.x BETWEEN 100 AND 200",

        "SELECT R.a, R.a B, R.a AS C FROM R",

        "SELECT R.a, S.\"b\" FROM R LEFT OUTER JOIN S ON R.x = S.x",

        "SELECT R.a, S.\"b\" FROM R LEFT OUTER JOIN S ON R.x = S.x LEFT OUTER JOIN T ON S.y = T.y",

        "SELECT R.a, S.\"b\" FROM R LEFT JOIN S ON R.x = S.x",

        "SELECT 'R'.a, S.b FROM R FULL JOIN S ON R.x = S.x",

		"SELECT 'R'.a, S.b FROM R FULL OUTER JOIN S ON R.x = S.x",

        "SELECT 'R'.a, S.b FROM R, S WHERE R.x = S.x",

        "SELECT 'R'.a, S.b FROM R FULL OUTER JOIN (S INNER JOIN T ON S.y = T.y) ON R.x = S.x",

        "SELECT 'R'.a, S.b FROM (R INNER JOIN S ON R.x=S.x) FULL OUTER JOIN (T INNER JOIN U ON T.y = U.y) ON S.q=T.q WHERE R.z = U.z",

        "SELECT 'R'.a, S.b FROM R INNER JOIN S ON R.x=S.x, (T INNER JOIN U ON T.y = U.y) WHERE R.z = U.z",

        "SELECT 'R'.a, S.b FROM R FULL OUTER JOIN S ON R.x = S.x",

        "SELECT CASE WHEN R.a=R.b THEN 'same' WHEN R.c IS NULL THEN 'different' ELSE R.c END FROM R",

        "SELECT R.a FROM R WHERE R.a LIKE 'a%'",
//		"SELECT R.a FROM R WHERE R.a LIKE 'a%' AND R.b LIKE 'a/%' ESCAPE '/'",

        "SELECT MS2SearchRuns.Flag,MS2SearchRuns.Links,MS2SearchRuns.Name,MS2SearchRuns.Created,MS2SearchRuns.RunGroups FROM MS2SearchRuns",

        "SELECT CURDATE() FROM R",
        "SELECT CONVERT(a, VARCHAR), CONVERT(a+b, SQL_INTEGER), CONVERT(c, 'SQL_TIMESTAMP'), CONVERT(d, 'TIMESTAMP') FROM R",
        "SELECT CONVERT(a, VARCHAR), CONVERT(a+b, SQL_INTEGER), CONVERT(c, 'SQL_Timestamp'), CONVERT(d, 'Timestamp') FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT CAST(a AS VARCHAR), CAST(a+b AS INTEGER) FROM R",
        "SELECT TIMESTAMPDIFF(SQL_TSI_SECOND,a,b), TIMESTAMPDIFF(SECOND,a,b), TIMESTAMPDIFF('SQL_TSI_DAY',a,b), TIMESTAMPDIFF('DAY',a,b) FROM R",
        "SELECT TIMESTAMPDIFF('SQL_TSI_Second',a,b), TIMESTAMPDIFF('Second',a,b), TIMESTAMPDIFF('SQL_TSI_Day',a,b), TIMESTAMPDIFF('Day',a,b) FROM R",
		"SELECT TIMESTAMPADD(SQL_TSI_SECOND,1,b), TIMESTAMPADD(SECOND,1,b), TIMESTAMPADD('SQL_TSI_DAY',1,b), TIMESTAMPADD('DAY',1,b) FROM R",

		"SELECT (SELECT value FROM S WHERE S.x=R.x) AS V FROM R",
		"SELECT R.value AS V FROM R WHERE R.y > (SELECT MAX(S.y) FROM S WHERE S.x=R.x)",
		"SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S) T ON R.z=T.z",

//		"SELECT R.a FROM R WHERE EXISTS (SELECT S.b FROM S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE NOT EXISTS (SELECT S.b FROM S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > ALL (SELECT value from S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > ANY (SELECT value from S WHERE S.x=R.x)",
//		"SELECT R.a FROM R WHERE R.value > SOME (SELECT value from S WHERE S.x=R.x)",

        "SELECT a FROM R WHERE a=b AND b<>c AND b!=c AND c>d AND d<e AND e<=f AND f>=g AND g IS NULL AND h IS NOT NULL " +
                " AND i BETWEEN 1 AND 2 AND j+k-l=-1 AND m/n=o AND p||q=r AND (NOT s OR t) AND u LIKE '%x%' AND u NOT LIKE '%xx%' " +
                " AND v IN (1,2) AND v NOT IN (3,4) AND x&y=1 AND x|y=1 AND x^y=1",

		"SELECT a FROM R UNION SELECT b FROM S",
        "SELECT a FROM R UNION ALL SELECT b FROM S",
		"(SELECT a FROM R) UNION ALL (SELECT b FROM S UNION (SELECT c FROM T)) ORDER BY a",
        "SELECT a, b FROM (SELECT a, b FROM R UNION SELECT a, b FROM S) U",

        // HAVING
        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' GROUP BY a,b HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",

        // comments
        "SELECT DISTINCT R.a, b AS B --nadlkf (*&F asdfl alsdkfj\nFROM rel R /* aldkjf (alsdf !! */ INNER JOIN S ON R.x=S.x WHERE R.y=0 AND R.a IS NULL OR R.b IS NOT NULL",

        "BROKEN",

        // nested JOINS
        "SELECT R.a, \"S\".b FROM R LEFT OUTER JOIN (S RIGHT OUTER JOIN T ON S.y = T.y) ON R.x = S.x",
        // .*
        "SELECT R.* FROM R",

        // PIVOT
        "SELECT R.a, R.b, SUM(x) sumX FROM R GROUP BY R.a, R.b PIVOT sumX BY b",
        "SELECT R.a, R.b, SUM(x) sumX FROM R GROUP BY R.a, R.b PIVOT sumX BY b IN (0,1,2)",
        "SELECT R.a, R.b, SUM(x) sumX FROM R GROUP BY R.a, R.b PIVOT sumX BY b IN (0 AS Zero,1 ONE,2 TWO)"
    };


    static String[] failSql = new String[]
    {
		"",
        "lutefisk",
        "SELECT R.a FROM R WHERE > 5", "SELECT R.a + AS A FROM R", "SELECT (R.a +) R.b AS A FROM R",
		"SELECT R.value, T.a, T.b FROM R INNER JOIN (SELECT S.a, S.b FROM S)",
        "SELECT \"a\",\"b\",AVG(x),COUNT(x),MIN(x),MAX(x),SUM(x),STDDEV(x) FROM R WHERE R.x='key' HAVING SUM(x)>100 ORDER BY a ASC, b DESC, SUM(x)",
        "SELECT SUM(*) FROM R",

        "BROKEN",
            
        // empty select list
        "SELECT FROM R",
        // missing FROM
        "SELECT R.a WHERE R.a > 5",
        // no table name
        "SELECT S.a AS lutefisk FROM",
        // no group by
        "SELECT R.a, R.b, SUM(x) sumX FROM R PIVOT sumX BY b IN (0,1,2)"
    };


    
    public static class SqlParserTestCase extends Assert
    {
        Pair<String,String>[] parseExprs = new Pair[]
        {
            // IDENT
            new Pair("a", "a"),
            new Pair("_a", "_a"),
            new Pair("$a", "$a"),
            new Pair("$_0", "$_0"),
            // QUOTED_IDENTIFIER
            new Pair("\"abcd\"", "\"abcd\""),
            new Pair("\"ab\"\"cd\"", "\"ab\"\"cd\""),
            // QUOTED_STRING
            new Pair("'abcdef'", "'abcdef'"),
            new Pair("'abc''def'", "'abc''def'"),
            // NUM_INT
            new Pair("123","123"),
            new Pair("-123.45","(- 123.45)"),
// HEX?           new Pair("0xff","0x00ff"),
            new Pair("1234567890L","1234567890"),
            new Pair("1.2e4","12000.0"),
            // OPERATORS and precedence
            new Pair("a = b","(= a b)"),
            new Pair("a < b","(< a b)"),
            new Pair("a > b","(> a b)"),
            new Pair("a <> b","(<> a b)"),
            new Pair("a != b","(!= a b)"),
            new Pair("a <= b","(<= a b)"),
            new Pair("a >= b","(>= a b)"),
            new Pair("a || b","(|| a b)"),
            new Pair("a + b","(+ a b)"),
            new Pair("a - b","(- a b)"),
            new Pair("a * b","(* a b)"),
            new Pair("a / b","(/ a b)"),
            new Pair("a | b","(| a b)"),
            new Pair("a ^ b","(^ a b)"),
            new Pair("a & b","(& a b)"),
            new Pair("-a","(- a)"),
            new Pair("+a","(+ a)"),
            new Pair("(a)","a"),
            new Pair("a IN (b)","(in a (IN_LIST b))"),
            new Pair("a IN (b,c)","(in a (IN_LIST b c))"),
            new Pair("a NOT IN (b,c)","(not in a (IN_LIST b c))"),
            new Pair("a BETWEEN 4 and 5", "(between a 4 5)"),
            new Pair("a NOT BETWEEN 4 and 5", "(not between a 4 5)"),
            new Pair("a LIKE 'b'", "(like a 'b')"),
            new Pair("a NOT LIKE 'b'", "(not like a 'b')"),

            new Pair("'a' || ('b' + 'c')", "(|| 'a' (+ 'b' 'c'))"),
            new Pair("a ^ -3 & 256", "(^ a (& (- 3) 256))"),
// CONCAT           new Pair("a OR b AND NOT b | c = d < e || f + g * -h", "")
            new Pair("a OR b AND NOT b | c = d < e + g * -h",
                     "(OR a (AND b (NOT (| b (= c (< d (+ e (* g (- h)))))))))"),
            new Pair("-a * b + c < d = e | f AND NOT b OR a",
                    "(OR (AND (| (= (< (+ (* (- a) b) c) d) e) f) (NOT b)) a)"),

            // identPrimary functions aggregates
            new Pair("a.b","(. a b)"),
            new Pair("a.b.fn(5)","(METHOD_CALL (. (. a b) fn) (EXPR_LIST 5))"),
            new Pair("CURDATE()","(METHOD_CALL CURDATE EXPR_LIST)"),
            new Pair("LCASE('a')","(METHOD_CALL LCASE (EXPR_LIST 'a'))"),
            new Pair("AGE(a,b)", "(METHOD_CALL AGE (EXPR_LIST a b))"),
            new Pair("SUM(a+b)","(SUM (+ a b))"),
            new Pair("CAST(a AS VARCHAR)", "(METHOD_CALL CAST (EXPR_LIST a 'VARCHAR'))")
        };
        Pair<String,String>[] parseStmts = new Pair[]
        {
            // joinExpression
            new Pair("SELECT * FROM R JOIN S ON x=y", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (RANGE R) (RANGE S) (ON (= x y))))))"),
            new Pair("SELECT * FROM R LEFT JOIN S ON x=y", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (RANGE R) LEFT (RANGE S) (ON (= x y))))))"),
            new Pair("SELECT * FROM R LEFT OUTER JOIN S ON x=y", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (RANGE R) LEFT (RANGE S) (ON (= x y))))))"),
            new Pair("SELECT * FROM R LEFT OUTER JOIN S ON x=y JOIN T ON y=z", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (JOIN (RANGE R) LEFT (RANGE S) (ON (= x y))) (RANGE T) (ON (= y z))))))"),
            new Pair("SELECT * FROM (R LEFT OUTER JOIN S ON x=y) JOIN T ON y=z", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (JOIN (RANGE R) LEFT (RANGE S) (ON (= x y))) (RANGE T) (ON (= y z))))))"),
            new Pair("SELECT * FROM R LEFT OUTER JOIN (S JOIN T on y=z) ON x=y", "(QUERY (SELECT_FROM (SELECT ROW_STAR) (FROM (JOIN (RANGE R) LEFT (JOIN (RANGE S) (RANGE T) (on (= y z))) (ON (= x y))))))"),
            // 11440
            new Pair("SELECT jrRuns.run_num FROM jrRuns WHERE ((SELECT max(jrRuns.run_num) FROM jrRuns) - jrRuns.run_num) < 10",
                    "(QUERY (SELECT_FROM (SELECT (. jrRuns run_num)) (FROM (RANGE jrRuns))) (WHERE (< (- (QUERY (SELECT_FROM (SELECT (max (. jrRuns run_num))) (FROM (RANGE jrRuns)))) (. jrRuns run_num)) 10)))")
        };

        private void good(String sql)
        {
            List<QueryParseException> errors = new ArrayList<QueryParseException>();
			QNode q = (new SqlParser()).parseQuery(sql,errors);
			if (errors.size() > 0)
				fail(errors.get(0).getMessage() + "\n" + sql);
			else
				assertNotNull(q);
        }


		private void bad(String sql)
		{
			List<QueryParseException> errors = new ArrayList<QueryParseException>();
			QNode q = (new SqlParser()).parseQuery(sql,errors);
			if (errors.size() == 0)
				fail("BAD: " + sql);
		}

		
        @Test
        public void test()
        {
            for (Pair<String,String> test : parseExprs)
            {
                List<QueryParseException> errors = new ArrayList<QueryParseException>();
                QExpr e = new SqlParser().parseExpr(test.first,errors);
                assertTrue(test.first + " no result and no error!", null != e || !errors.isEmpty());
                assertTrue(test.first + " has parse errors", errors.isEmpty());
                assertNotNull(test.first + " did not parse", e);
                String prefix = toPrefixString(e);
                assertEquals(test.second,prefix);
            }
            for (Pair<String,String> test : parseStmts)
            {
                List<QueryParseException> errors = new ArrayList<QueryParseException>();
                QNode e = new SqlParser().parseQuery(test.first,errors);
                assertTrue(test.first + " no result and no error!", null != e || !errors.isEmpty());
                assertTrue(test.first + " has parse errors", errors.isEmpty());
                assertNotNull(test.first + " did not parse", e);
                String prefix = toPrefixString(e);
                assertEquals(test.first, test.second, prefix);
            }
            for (String sql : testSql)
            {
                try
                {
                    if (sql.equals("BROKEN"))
                        break;
                    good(sql);
                }
                catch (Throwable t)
                {
                    fail(t.getMessage() + "\n" + sql);
                }
            }
            for (String sql : failSql)
            {
                try
                {
                    if (sql.equals("BROKEN"))
                        break;
					bad(sql);
                }
                catch (Throwable t)
                {
                    fail(sql);
                }
            }
        }
    }
}
