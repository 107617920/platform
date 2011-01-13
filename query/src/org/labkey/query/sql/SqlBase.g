grammar SqlBase;

options
{
    language=Java;
    output=AST;
    memoize=true;
    backtrack=true; // primaryExpression : OPEN! ( subQuery | expression ) CLOSE!
    ASTLabelType=CommonTree;
}

//
// SQL language grammar.
// This source code was modified from the Hibernate project ('hql.g').
//

tokens
{
    ASCENDING;
	AGGREGATE;		// One of the aggregate functions (e.g. min, max, avg)
	ALIAS;
    DECLARATION; 
	DESCENDING;
	EXPR_LIST;
	IN_LIST;
	IS_NOT;
	METHOD_CALL;
	NOT_BETWEEN;
	NOT_IN;
	NOT_LIKE;
    PARAMETERS;
	QUERY;
	RANGE;
	ROW_STAR;
	SELECT_FROM;
    STATEMENT;
    DATATYPE;
	UNARY_MINUS;
	UNARY_PLUS;
	UNION_ALL;
}


@header
{
	package org.labkey.query.sql.antlr;
}


@lexer::header
{
	package org.labkey.query.sql.antlr;

    import org.apache.log4j.Category;
    import org.labkey.query.sql.SqlParser;
}


@members
{
    /**
     * This method looks ahead and converts . <token> into . IDENT when
     * appropriate.
     */
    public void handleDotIdent() //throws TokenStreamException
    {
    }

	public void weakKeywords() //throws TokenStreamException
	{
	}

	public boolean isSqlType(String type)
	{
	    return true;
	}

    @Override
    public void traceOut(String ruleName, int ruleIndex, Object inputSymbol)
    {
        super.traceOut(ruleName, ruleIndex, inputSymbol);
    }
}


@lexer::members
{
    Category _log = Category.getInstance(SqlParser.class);
    
    protected void setPossibleID(boolean possibleID)
    {
    }

    @Override
    public void emitErrorMessage(String msg)
    {
        _log.debug(msg);
    }
}


//
// SQL TOKENS
//

ALL : 'all';
ANY : 'any';
AND : 'and';
AS : 'as';
AVG : 'avg';
BETWEEN : 'between';
CASE : 'case';
CAST : 'cast';
COUNT : 'count';
DELETE : 'delete';
DISTINCT : 'distinct';
DOT : '.';
ELSE : 'else';
END : 'end';
ESCAPE : 'escape';
EXISTS : 'exists';
FALSE : 'false';
FROM : 'from';
FULL : 'full';
GROUP : 'group';
HAVING : 'having';
IN : 'in';
INNER : 'inner';
INSERT : 'insert';
INTO : 'into';
IS : 'is';
JOIN : 'join';
LEFT : 'left';
LIKE : 'like';
LIMIT : 'limit';
MAX : 'max';
GROUP_CONCAT : 'group_concat';
MIN : 'min';
NOT : 'not';
NULL : 'null';
ON : 'on';
OR : 'or';
ORDER : 'order';
OUTER : 'outer';
PIVOT : 'pivot';
RIGHT : 'right';
SELECT : 'select';
SET : 'set';
SOME : 'some';
STDDEV : 'stddev';
SUM : 'sum';
THEN : 'then';
TRUE : 'true';
UNION : 'union';
UPDATE : 'update';
WHERE : 'where';
WHEN : 'when';


//
// SQL GRAMMAR
//


statement
	: parameters? ( updateStatement | deleteStatement | selectStatement | insertStatement )
	    -> ^(STATEMENT parameters? updateStatement? deleteStatement? selectStatement? insertStatement?)
	;


parameters
    :   'parameters' OPEN declarationList CLOSE -> ^(PARAMETERS declarationList)
    ;


declarationList
	: declaration (COMMA! declaration)*
    ;


declaration
    : identifier sqltype ('default' constant)? -> ^(DECLARATION identifier sqltype constant?)
    ;


sqltype
    : type=identifier
    {
        if (!isSqlType($type.text))
            reportError(new MismatchedTokenException(DATATYPE, input));
    }
    ;
    

updateStatement
	: UPDATE^ 
		optionalFromTokenFromClause
		setClause
		(whereClause)?
	;

setClause
	: (SET^ assignment (COMMA! assignment)*)
	;

assignment
	: stateField EQ^ newValue
	;

// 'state_field' is the term used in the EJB3 sample grammar; used here for easy reference.
// it is basically a property ref
stateField
	: path
	;

// this still needs to be defined in the ejb3 spec; additiveExpression is currently just a best guess,
// although it is highly likely I would think that the spec may limit this even more tightly.
newValue
	: valueExpression
	;

deleteStatement
	: DELETE^
		(optionalFromTokenFromClause)
		(whereClause)?
	;

optionalFromTokenFromClause!
	: (FROM)? f=path (a=(AS? identifier))?
	    -> ^(FROM $f $a)
	;

selectStatement
	: q=union
		((o=orderByClause! {$q.tree.addChild($o.tree);})?)
		((l=limitClause! {$q.tree.addChild($l.tree);})?)
	;

insertStatement
	// Would be nice if we could abstract the FromClause/FromElement logic
	// out such that it could be reused here; something analogous to
	// a 'table' rule in sql-grammars
	: INSERT^ intoClause select
	;

intoClause
	: INTO^ path { weakKeywords(); } insertablePropertySpec
	;

insertablePropertySpec
	: OPEN! primaryExpression ( COMMA! primaryExpression )* CLOSE!
	;


union
  : unionTerm (u=UNION^ (ALL! { $u.tree.getToken().setType(UNION_ALL); } )? unionTerm)*
  ;


unionTerm
  : select
  | OPEN! union CLOSE!
  ;


select
	: (selectFrom (whereClause)? (groupByClause (havingClause)? (pivotClause)?)?)
	    -> ^(QUERY selectFrom whereClause? groupByClause? havingClause? pivotClause?)
    ;


selectFrom!
	: (selectClause fromClause?)
	    -> ^(SELECT_FROM selectClause fromClause?)
	;


selectClause
	: SELECT^ { weakKeywords(); }	// Weak keywords can appear immediately after a SELECT token.
		(DISTINCT)? ( selectedPropertiesList )
	;


// NOTE: This *must* begin with the 'FROM' token, otherwise the sub-query rule will be ambiguous
// with the expression rule.
// Also note: after a comma weak keywords are allowed and should be treated as identifiers.

fromClause
	: FROM^ { weakKeywords(); } joinExpression (COMMA! { weakKeywords(); } joinExpression )*
	;

joinExpression
	: ((fromRange) -> fromRange) (((((LEFT|RIGHT|FULL) (OUTER)?) | INNER)? JOIN fromRange onClause) -> ^(JOIN $joinExpression LEFT? RIGHT? FULL? INNER? fromRange onClause))*
    ;

fromRange
	: (path { weakKeywords(); } (AS? identifier)?) -> ^(RANGE path identifier?)
	| OPEN
	    ( (subQuery) => subQuery CLOSE AS? identifier -> ^(RANGE subQuery identifier)
	    | joinExpression CLOSE -> joinExpression
	    )
	;


onClause
	: ON^ logicalExpression
	;


groupByClause
	: GROUP^ 'by'! expression ( COMMA! expression )*
	;


pivotClause
    : PIVOT^ identifierList 'by'! identifier IN! OPEN! constantList CLOSE!
    ;
    

orderByClause
	: ORDER^ 'by'! orderElement ( COMMA! orderElement )*
	;


limitClause
    : LIMIT^ NUM_INT;


orderElement
	: expression ( ascendingOrDescending )?
	;


ascendingOrDescending
	: ( 'asc' | 'ascending' )   -> ^(ASCENDING)
	| ( 'desc' | 'descending') 	-> ^(DESCENDING)
	;


havingClause
	: HAVING^ logicalExpression
	;


whereClause
	: WHERE^ logicalExpression
	;


selectedPropertiesList
    // weird trailing comma for backward compatibility
	: aliasedSelectExpression (COMMA! aliasedSelectExpression)* (COMMA!)?
	;


aliasedSelectExpression
	: (expression (AS? identifier)?) -> ^(ALIAS expression identifier?)
	| starAtom
	;


identifierList
	: identifier (COMMA identifier)* -> ^(EXPR_LIST identifier*)
	;


constantList
	: constant (COMMA constant)* -> ^(EXPR_LIST constant*)
	;


// expressions
// Note that most of these expressions follow the pattern
//   thisLevelExpression :
//       nextHigherPrecedenceExpression
//           (OPERATOR nextHigherPrecedenceExpression)*
// which is a standard recursive definition for a parsing an expression.
//
// Operator precedence in HQL
// lowest  --> ( 7)  OR
//             ( 6)  AND, NOT
//             ( 5)  equality: ==, <>, !=, is
//             ( 4)  relational: <, <=, >, >=,
//                   LIKE, NOT LIKE, BETWEEN, NOT BETWEEN, IN, NOT IN
//             ( 3)  addition and subtraction: +(binary) -(binary)
//             ( 2)  multiplication: * / %, concatenate: ||
// highest --> ( 1)  +(unary) -(unary)
//                   []   () (method call)  . (dot -- identifier qualification)
//                   aggregate function
//                   ()  (explicit parenthesis)
//
// Note that the above precedence levels map to the rules below...
// Once you have a precedence chart, writing the appropriate rules as below
// is usually very straightfoward

logicalExpression
	: expression
	;

// Main expression rule
expression
	: logicalOrExpression
	;

// level 7 - OR
logicalOrExpression
	: logicalAndExpression ( OR^ logicalAndExpression )*
	;

// level 6 - AND, NOT
logicalAndExpression
	: negatedExpression ( AND^ negatedExpression )*
	;

negatedExpression
	 // Weak keywords can appear in an expression, so look ahead.
	: NOT^ negatedExpression
	| { weakKeywords(); } bitwiseOrExpression
	;

bitwiseOrExpression
    : equalityExpression ( (BIT_OR^ | BIT_XOR^) equalityExpression ) *
    ;

//## OP: EQ | LT | GT | LE | GE | NE | SQL_NE | LIKE;

// level 5 - EQ, NE
equalityExpression
	: relationalExpression (
		( EQ^
		| is=IS^ (NOT!  { $is.tree.getToken().setType(IS_NOT); } )?
		| NE^
		| ne=SQL_NE^ { $ne.tree.getToken().setType(NE); }
		)
      relationalExpression)?
	;

// level 4 - LT, GT, LE, GE, LIKE, NOT LIKE, BETWEEN, NOT BETWEEN
// NOTE: The NOT prefix for LIKE and BETWEEN will be represented in the
// token type.  When traversing the AST, use the token type, and not the
// token text to interpret the semantics of these nodes.


relationalExpression
	: valueExpression (
		( ( (LT|GT|LE|GE)^ additiveExpression )? )
		| (n=NOT!)? (
			// Represent the optional NOT prefix using the token type by
			// testing 'n' and setting the token type accordingly.
			(i=IN^ {
					$i.tree.getToken().setType( ($n == null) ? IN : NOT_IN);
					$i.tree.getToken().setText( ($n == null) ? "in" : "not in");
				}
				inList)
			| (b=BETWEEN^ {
					$b.tree.getToken().setType( ($n == null) ? BETWEEN : NOT_BETWEEN);
					$b.tree.getToken().setText( ($n == null) ? "between" : "not between");
				}
				betweenList )
			| (l=LIKE^ {
					$l.tree.getToken().setType( ($n == null) ? LIKE : NOT_LIKE);
					$l.tree.getToken().setText( ($n == null) ? "like" : "not like");
				}
				valueExpression likeEscape)
            )
		)
	;

likeEscape
	: (ESCAPE^ valueExpression)?
	;

inList
	: compoundExpr -> ^(IN_LIST compoundExpr)
	;

betweenList
	: valueExpression AND! valueExpression
	;

//level 4 - string concatenation
// concatenation is the highest non-boolean expression
valueExpression : concatenation ;

concatenation
	: additiveExpression ( CONCAT^ additiveExpression )*
	;

// level 3 - binary plus and minus
additiveExpression
	: multiplyExpression ( ( PLUS^ | MINUS^ | BIT_AND^ ) multiplyExpression )*
	;

// level 2 - binary multiply and divide
multiplyExpression
	: unaryExpression ( ( STAR^ | DIV^ ) unaryExpression )*
	;
	
// level 1 - unary minus, unary plus, not
unaryExpression
	: (m=MINUS^ {$m.tree.getToken().setType(UNARY_MINUS);}) unaryExpression
	| (p=PLUS^ {$p.tree.getToken().setType(UNARY_PLUS);}) unaryExpression
	| caseExpression                                                           
	| quantifiedExpression
	| atom
	;
	
caseExpression
	: CASE^ (whenClause)+ (elseClause)? END! 
	;
	
whenClause
	: (WHEN^ logicalExpression THEN! unaryExpression)
	;
	
altWhenClause
	: (WHEN^ unaryExpression THEN! unaryExpression)
	;
	
elseClause
	: (ELSE^ unaryExpression)
	;
	

quantifiedExpression
	: ( SOME^ | EXISTS^ | ALL^ | ANY^ ) 
	( identifier | (OPEN! ( subQuery ) CLOSE!) )
	;


// level 0 - expression atom
// ident qualifier ('.' ident ), array index ( [ expr ] ),
atom
    : primaryExpression ( DOT^ (identifier | starAtom) )*
	;

fragment
starAtom
    : STAR -> ROW_STAR
    ;

// level 0 - the basic element of an expression
primaryExpression
	:   identPrimary
	|   constant
	|   OPEN! ( subQuery | expression ) CLOSE!
	|   PARAM^ (NUM_INT)?
	;


// identifier, followed by member refs (dot ident), or method calls.
// NOTE: handleDotIdent() is called immediately after the first IDENT is recognized because
// the method looks ahead to find keywords after DOT and turns them into identifiers.
identPrimary
	: identifier { handleDotIdent(); }
        ( options { greedy=true; } : (DOT^ identifier) )*
        ( options { greedy=true; } : op=OPEN^ {$op.tree.getToken().setType(METHOD_CALL);} exprList CLOSE! )?
	| aggregate
	| cast
// UNDONE: figure out the weakKeywords thing
	| l=LEFT  {$l.tree.getToken().setType(IDENT);} op=OPEN^ {$op.tree.getToken().setType(METHOD_CALL);} exprList CLOSE!
	| r=RIGHT {$r.tree.getToken().setType(IDENT);} op=OPEN^ {$op.tree.getToken().setType(METHOD_CALL);} exprList CLOSE!
	;


aggregate
    @after {$aggregate.tree.getToken().setType(AGGREGATE);}
	: (( SUM^ | AVG^ | MAX^ | MIN^ | STDDEV^ ) OPEN! expr=(additiveExpression) CLOSE!)
	| (COUNT^ OPEN! d=DISTINCT? expr=(additiveExpression | starAtom) CLOSE!)
	| (GROUP_CONCAT^ OPEN! d=DISTINCT? expr=(additiveExpression) CLOSE!)
	;


cast
    : (c=CAST open=OPEN expression as=AS sqltype CLOSE)
        -> ^(METHOD_CALL IDENT[$c] ^(EXPR_LIST expression sqltype))
	;


compoundExpr
	: path
	| OPEN! ( subQuery| expression (COMMA! expression)* ) CLOSE!
	;


subQuery
	: union
	;

exprList
	: exprListFragment -> ^(EXPR_LIST exprListFragment?)
 	;

exprListFragment
    : (expression (COMMA! expression)*)?
    ; 

constant
	: number
	| QUOTED_STRING
	| NULL
	| TRUE
	| FALSE
	;

number
    : NUM_INT
    | NUM_LONG
    | NUM_DOUBLE
    | NUM_FLOAT
    ;

path
	: identifier ( DOT^ { weakKeywords(); } identifier )*
	;


// NOTE: left() and right() are functions as well as keywords
// NOTE: would be nice to get weakKeywords() working in general
identifier
	: IDENT | QUOTED_IDENTIFIER
	;


// **** LEXER ******************************************************************

/**
 * Hibernate Query Language Lexer
 * <br>
 * This lexer provides the HQL parser with tokens.
 * @author Joshua Davis (pgmjsd@sourceforge.net)
 */


EQ: '=';
LT: '<';
GT: '>';
SQL_NE: '<>';
NE: '!=';
LE: '<=';
GE: '>=';

COMMA: ',';

OPEN: '(';
CLOSE: ')';

CONCAT: '||';
PLUS: '+';
MINUS: '-';
STAR: '*';
DIV: '/';
COLON: ':';
PARAM: '?';
BIT_OR: '|';
BIT_XOR: '^';
BIT_AND: '&';

IDENT
	: ID_START_LETTER ( ID_LETTER )*
		{
    		// Setting this flag allows the grammar to use keywords as identifiers, if necessary.
			setPossibleID(true);
		}
	;

fragment
ID_START_LETTER
    :    '_'
    |    '$'
    |    'a'..'z'
    |    'A'..'Z'
    |    '\u0080'..'\ufffe'       // HHH-558 : Allow unicode chars in identifiers
    ;


fragment
ID_LETTER
    :    ID_START_LETTER
    |    '0'..'9'
    ;


QUOTED_STRING
	: '\'' (~'\'' | '\'\'')* '\''
	;


QUOTED_IDENTIFIER 
	: '"' (~'"' | '""')* '"'
	;


WS 
	: (' ' | '\t' | '\r' | '\n')+ {skip();}
	;

//--- From the Java example grammar ---

fragment NUM_LONG : ;
fragment NUM_DOUBLE : ;
fragment NUM_FLOAT : ;

// a numeric literal
NUM_INT
	@init {boolean isDecimal=false; Token t=null;}
	:   '.' {_type = DOT;}
			(	('0'..'9')+ (EXPONENT)? (f1=FLOAT_SUFFIX {t=f1;})?
				{
					if (t != null && t.getText().toUpperCase().indexOf('F')>=0)
					{
						_type = NUM_FLOAT;
					}
					else
					{
						_type = NUM_DOUBLE; // assume double
					}
				}
			)?
	|	(	'0' {isDecimal = true;} // special case for just '0'
			(	('x')
				(											// hex
				:	HEX_DIGIT
				)+
			|	('0'..'7')+									// octal
			)?
		|	('1'..'9') ('0'..'9')*  {isDecimal=true;}		// non-zero decimal
		)
		(	('l') { _type = NUM_LONG; }

		// only check to see if it's a float if looks like decimal so far
		|	{isDecimal}?
			(   '.' ('0'..'9')* (EXPONENT)? (f2=FLOAT_SUFFIX {t=f2;})?
			|   EXPONENT (f3=FLOAT_SUFFIX {t=f3;})?
			|   f4=FLOAT_SUFFIX {t=f4;}
			)
			{
				if (t != null && t.getText().toUpperCase() .indexOf('F') >= 0)
				{
					_type = NUM_FLOAT;
				}
				else
				{
					_type = NUM_DOUBLE; // assume double
				}
			}
		)?
	;

// hexadecimal digit (again, note it's protected!)
fragment
HEX_DIGIT
	:	('0'..'9'|'a'..'f'|'A'..'F')
	;

// a couple protected methods to assist in matching floating point numbers
fragment
EXPONENT
	:	('e') ('+'|'-')? ('0'..'9')+
	;

fragment
FLOAT_SUFFIX
	:	'f'|'d'
	;

COMMENT
    :   '/*' (options {greedy=false;} : . )* '*/' {skip();}
    ;
    
LINE_COMMENT
    : '--' (~('\r'|'\n'))* ('\r\n' | '\n' | '\r')? {skip();}
    ;
