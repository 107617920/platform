/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MemTracker;
import org.labkey.data.xml.ColumnType;
import org.labkey.query.design.DgColumn;
import org.labkey.query.design.DgOrderByString;
import org.labkey.query.design.DgQuery;
import org.labkey.query.design.DgValue;
import org.labkey.query.design.QueryDocument;
import org.labkey.query.sql.antlr.SqlBaseParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class QuerySelect extends QueryRelation
{
    private String _queryText = null;
    private Map<FieldKey, SelectColumn> _columns;

    // these three fields are accessed by QueryPivot
    QGroupBy _groupBy;
    QOrder _orderBy;
    QLimit _limit;

    private QSelect _select;
    private QWhere _where;
    private QWhere _having;
    QQuery _root;
    private QDistinct _distinct;

    private Map<FieldKey, QTable> _parsedTables;
    private List<QJoinOrTable> _parsedJoins;
    private List<QExpr> _onExpressions;

    private Map<FieldKey, QueryRelation> _tables;
    private Map<FieldKey, RelationColumn> _declaredFields = new HashMap<FieldKey, RelationColumn>();
    private SQLTableInfo _subqueryTable;
    private AliasManager _aliasManager;

    private Set<FieldKey> parametersInScope = new HashSet<FieldKey>(); 


    private QuerySelect(@NotNull Query query, @NotNull QuerySchema schema, String alias)
    {
        super(query, schema, alias == null ? "_select" + query.incrementAliasCounter() : alias);

        // subqueryTable is only for expr.createColumnInfo()
        // should refactor so tableinfo is not necessary, maybe expr.setColumnAttributes(target)
        _subqueryTable = new SQLTableInfo(_schema.getDbSchema());

        _aliasManager = new AliasManager(schema.getDbSchema());
        _queryText = query._querySource;

        for (QParameter p : query._parameters)
            parametersInScope.add(new FieldKey(null, p.getName()));
        
        assert MemTracker.put(this);
    }


	QuerySelect(@NotNull Query query, QQuery root)
	{
		this(query, query.getSchema(), null);
        this._query = query;
		initializeFromQQuery(root);
		initializeSelect();
        assert MemTracker.put(this);
	}


    // create a simple QuerySelect over a QRelation
    QuerySelect(QueryRelation from, QOrder order, QLimit limit)
    {
        this(from._query, from.getSchema(), null);

        String alias = StringUtils.defaultString(from.getAlias(), "T");
        from.setAlias(alias);

        _orderBy = order;
        _limit = limit;
        
        QTable t = new QTable(from, alias);
        _parsedJoins = new ArrayList<QJoinOrTable>();
        _parsedJoins.add(t);
        _onExpressions = Collections.EMPTY_LIST;
        _parsedTables = new LinkedHashMap<FieldKey, QTable>();
        _parsedTables.put(t.getTableKey(), t);
        _select = new QSelect();
        _select.childList().add(new QRowStar());
        initializeSelect();
    }


    QueryRelation createSubquery(QQuery qquery, boolean inFromClause, String alias)
    {
        QueryRelation sub = Query.createQueryRelation(this._query, qquery);
        sub._parent = this;
        sub._inFromClause = inFromClause;
        if (null != alias)
            sub.setAlias(alias);
        return sub;
    }


    @Override
    void setQuery(Query query)
    {
//        assert getParseErrors().size() == 0;
        super.setQuery(query);
        for (QueryRelation r : _tables.values())
            r.setQuery(query);
    }


    /*
     * used by QueryPivot
     * find each group by field in the select list
     * add a new column if we can't find it
     */
    Map<String,SelectColumn> getGroupByColumns()
    {
        // CONSIDER: find selected group keys if they are already in the select list
        Map<String,SelectColumn> ret = new HashMap<String,SelectColumn>();
        int index = 0;
groupByLoop:        
        for (QNode gb : _groupBy.childList())
        {
            index++;
            for (SelectColumn c : _columns.values())
            {
                QNode n = c._field;
                if (n.equals(gb))
                {
                    ret.put(c.getName(), c);
                    continue groupByLoop;
                }
            }
            QExpr copy = ((QExpr)gb).copyTree();
            SelectColumn col = new SelectColumn(copy, "__gb_key__" + index);
            _columns.put(col.getFieldKey(), col);
            ret.put(col.getName(), col);
        }
        return ret;
    }

    private void initializeFromQQuery(QQuery root)
    {
        _root = root;
        if (root == null)
            return;
        _limit = root.getLimit();
        QSelect select = root.getSelect();

        QFrom from = root.getFrom();
        if (from == null)
        {
            _parsedTables = Collections.EMPTY_MAP;
            _parsedJoins = Collections.EMPTY_LIST;
            _onExpressions = Collections.EMPTY_LIST;
        }
        else
        {
            FromParser p = new FromParser(from);
            _parsedTables = p.getTables();
            _parsedJoins = p.getJoins();
            _onExpressions = p.getOns();
        }
        _select = root.getSelect();
        _where = root.getWhere();
        _having = root.getHaving();
        _groupBy = root.getGroupBy();
        _orderBy = root.getOrderBy();
    }


    private void initializeSelect()
    {
        _columns = new LinkedHashMap<FieldKey,SelectColumn>();
        _tables = new HashMap<FieldKey, QueryRelation>();

        for (QTable qtable : _parsedTables.values())
        {
            FieldKey key = qtable.getTableKey();
            String alias = qtable.getAlias().getName();
            QNode node = qtable.getTable();

            QueryRelation relation;
            if (null != qtable.getQueryRelation())
            {
                relation = qtable.getQueryRelation();
            }
            else if (node instanceof QQuery)
            {
                relation = createSubquery((QQuery) node, true, alias);
            }
            else if (node instanceof  QUnion)
            {
                relation = new QueryUnion(this, (QUnion) node, true, alias);
            }
            else
            {
                relation = _query.resolveTable(_schema, node, key, alias);
                assert relation == null || alias.equals(relation.getAlias());
                if (null != relation)
                    relation._parent = this;
            }
            assert relation != null || !getParseErrors().isEmpty();
            if (relation == null)
                continue;
            qtable.setQueryRelation(relation);
            String relationAlias = makeRelationName(relation.getAlias());
            relation.setAlias(relationAlias);
            _tables.put(qtable.getAlias(), relation);
        }

        ArrayList<SelectColumn> columnList = new ArrayList<SelectColumn>();
        if (_select != null)
        {
            LinkedList<QNode> process = new LinkedList(_select.childList());
            while (!process.isEmpty())
            {
                QNode node = process.removeFirst();
                
                if (node instanceof QDistinct)
                {
                    if (!columnList.isEmpty())
                        parseError("DISTINCT not expected", node);
                    else
                        _distinct = (QDistinct)node;
                    continue;
                }

                if (node instanceof QRowStar)
                {
                    for (QTable t : _parsedTables.values())
                    {
                        QNode tableStar = QFieldKey.of(new FieldKey(t.getAlias(), "*"));
                        tableStar.setLineAndColumn(node);
                        process.addFirst(tableStar);
                    }
                    continue;
                }

                // look for table.*
                if (node instanceof QFieldKey)
                {
                    FieldKey key = ((QFieldKey)node).getFieldKey();

                    if (null != key && key.getName().equals("*"))
                    {
                        FieldKey parent = key.getParent();
                        if (parent.getParent() != null)
                        {
                            parseError("Can't resolve column: " + node.getSourceText(), node);
                            continue;
                        }
                        QueryRelation r = getTable(parent);
                        if (null == r)
                        {
                            parseError("Can't resolve column: " + node.getSourceText(), node);
                            continue;
                        }
                        for (String name :  r.getAllColumns().keySet())
                        {
                            columnList.add(new SelectColumn(new FieldKey(parent,name)));
                        }
                        continue;
                    }
                }
                columnList.add(new SelectColumn(node));
            }
        }

        if (_parent != null && !_inFromClause && columnList.size() != 1)
        {
            parseError("Subquery can have only one column.", _root);
            return;
        }

        // two passes to maintain ordering and avoid the odd name collision
        // create unique aliases where missing
        int expressionUniq = 0;
        Map<FieldKey,FieldKey> fieldKeys = new HashMap<FieldKey,FieldKey>();
        for (SelectColumn column : columnList)
        {
            String name = column.getName();
            if (null == name)
                continue;
            if (null == column._key)
                column._key = new FieldKey(null,name);
            if (fieldKeys.containsKey(column._key))
                parseError("Duplicate column '" + column.getName() + "'", column.getField());
            fieldKeys.put(column._key, column._key);
        }
        for (SelectColumn column : columnList)
        {
            String name = column.getName();
            if (null != name)
                continue;
            while (fieldKeys.containsKey(new FieldKey(null,"Expression" + ++expressionUniq)))
                ;
            name = "Expression" + expressionUniq;
            if (null == column._key)
                column._key = new FieldKey(null,name);
            fieldKeys.put(column._key, column._key);
        }
        for (SelectColumn column : columnList)
        {
            if (null == column.getAlias())
                column.setAlias(_aliasManager.decideAlias(column.getName()));
            _columns.put(column._key, column);
        }
    }



    private SqlDialect getSqlDialect()
    {
        return getDbSchema().getSqlDialect();
    }


    private DbSchema getDbSchema()
    {
        return _schema.getDbSchema();
    }


    public TableInfo getFromTable(FieldKey key)
    {
        QueryRelation qr = getTable(key);
        if (qr != null)
            return qr.getTableInfo();
        return null;
    }


    public String getQueryText()
    {
        if (!getParseErrors().isEmpty() && _columns == null)
        {
            return "ERROR";
        }
        SourceBuilder builder = new SourceBuilder();
        if (null == _distinct)
            builder.pushPrefix("SELECT ");
        else
            builder.pushPrefix("SELECT DISTINCT ");
        for (SelectColumn column : _columns.values())
        {
            column.appendSource(builder);
            builder.nextPrefix(",\n");
        }
        builder.popPrefix();
        builder.pushPrefix("\nFROM ");
        for (QJoinOrTable range : _parsedJoins)
        {
            range.appendSource(builder);
            builder.nextPrefix("\n");
        }
        builder.popPrefix();
        if (null != _where)
        {
            _where.appendSource(builder);
        }
        if (null != _groupBy)
        {
            _groupBy.appendSource(builder);
        }
        if (null != _having)
        {
            assert null != _groupBy;
            _having.appendSource(builder);
        }
        if (_orderBy != null)
        {
            _orderBy.appendSource(builder);
        }
        if (_limit != null)
        {
            _limit.appendSource(builder);
        }
        return builder.getText();
    }


    public Set<FieldKey> getFromTables()
    {
        return _parsedTables.keySet();
    }




    private class FromParser
    {
        final Map<FieldKey, QTable> _tables;
        final List<QJoinOrTable> _joins;        // root joins, these may be trees
        final List<QExpr> _ons;                 // on expressions for declareFields
        
        FromParser(QFrom from)
        {
            _tables = new LinkedHashMap<FieldKey, QTable>();
            _joins = new ArrayList<QJoinOrTable>();
            _ons = new ArrayList<QExpr>();
            parseFrom(from);
        }


        Map<FieldKey, QTable> getTables()
        {
            return _tables;
        }


        List<QJoinOrTable> getJoins()
        {
            return _joins;
        }


        List<QExpr> getOns()
        {
            return _ons;
        }


        private void parseFrom(QNode from)
        {
            List<QNode> l = from.childList();
            if (l.isEmpty())
            {
                parseError("FROM clause is empty", from);
                return;
            }
            for (QNode r : from.childList())
            {
                QJoinOrTable q = parseNode(r);
                if (null != q)
                    _joins.add(q);
                else
                    assert !getParseErrors().isEmpty();
            }
        }

        private QJoinOrTable parseNode(QNode r)
        {
            if (r.getTokenType() == SqlBaseParser.RANGE)
                return parseRange(r);
            else if (r.getTokenType() == SqlBaseParser.JOIN)
                return parseJoin(r);
            else
            {
                parseError("Error in FROM clause", r);
                return null;
            }
        }


        private QTable parseRange(QNode node)
        {
            int countChildren = node.childList().size();
            if (countChildren  < 1 || 2 < countChildren || !(node.childList().get(0) instanceof QExpr))
            {
                parseError("Syntax error in JOIN clause", node);
                return null;
            }

            List<QNode> children = node.childList();
            QExpr expr = (QExpr) children.get(0);
            QIdentifier alias = null;
            if (children.size() > 1 && children.get(1) instanceof QIdentifier)
                alias = (QIdentifier) children.get(1);

            QTable table = new QTable(expr);
            table.setAlias(alias);
            FieldKey aliasKey = table.getAlias();
            if (_tables.containsKey(aliasKey))
            {
                parseError(aliasKey + " was specified more than once", table.getTable());
            }
            else
            {
                _tables.put(aliasKey, table);
            }
            return table;
        }


        private QJoin parseJoin(QNode join)
        {
            List<QNode> children = join.childList();
            if (children.size() < 3 || children.size() >  4)
            {
                parseError("Error in JOIN clause", join);
                return null;
            }

            int childIndex = 0;

            QNode leftNode = children.get(childIndex++);
            QJoinOrTable left = parseNode(leftNode);

            JoinType joinType = JoinType.inner;
            switch (children.get(childIndex++).getTokenType())
            {
                case SqlBaseParser.LEFT:
                    joinType = JoinType.left;
                    break;
                case SqlBaseParser.RIGHT:
                    joinType = JoinType.right;
                    break;
                case SqlBaseParser.INNER:
                    joinType = JoinType.inner;
                    break;
                case SqlBaseParser.OUTER:
                    joinType = JoinType.outer;
                    break;
                case SqlBaseParser.FULL:
                    joinType = JoinType.full;
                    break;
                case SqlBaseParser.CROSS:
                    joinType = JoinType.cross;
                    break;
                default:
                    childIndex--;
                    break;
            }

            QNode rightNode = children.get(childIndex++);
            QJoinOrTable right = parseNode(rightNode);

            QNode on = null;
            if (children.size() > childIndex)
            {
                on = children.get(childIndex);
                if (on.childList().size() != 1 || !(on.childList().get(0) instanceof QExpr))
                {
                    parseError("Error in ON expression", on);
                    return null;
                }
                _ons.add((QExpr)on.childList().get(0));
            }

            if (joinType == JoinType.cross && null != on)
                parseError("ON unexpected in a CROSS JOIN", on);
            else if (joinType != JoinType.cross && null == on)
                parseError("ON expected", rightNode);
            QJoin qjoin = new QJoin(left, right, joinType, null==on ? null : (QExpr)on.childList().get(0));
            return qjoin;
        }
    }


    private QueryRelation getTable(FieldKey key)
    {
        QueryRelation ret = _tables.get(key);
        return ret;
    }


    String makeRelationName(String name)
    {
        if (!AliasManager.isLegalName(name) || name.length() > 40)
            return AliasManager.makeLegalName(name, getSqlDialect()) + "_" + _query.incrementAliasCounter();
        else
            return name + "_" + _query.incrementAliasCounter();
    }


    /**
     * Resolve a particular field.
     * The FieldKey may refer to a particular ColumnInfo.
     * Also, if expr is a child of a QMethodCall, then the FieldKey may refer to a {@link org.labkey.query.sql.Method}
     * or a {@link org.labkey.api.data.TableInfo#getMethod}
     *
     * CONSIDER: mark method identifiers...
     */
    @Override
    protected QField getField(FieldKey key, QNode expr)
    {
        return getField(key, expr, false);
    }

    private QField getField(FieldKey key, QNode expr, boolean methodName)
    {
        if (!methodName)
        {
            RelationColumn column = _declaredFields.get(key);
            if (column != null)
                return new QField(column, expr);
        }

        if (key.getTable() == null)
        {
            return new QField(null, key.getName(), expr);
        }
        else
        {
            QueryRelation table = getTable(key.getTable());
            if (table != null)
            {
                return new QField(table, key.getName(), expr);
            }
            return super.getField(key,expr);
        }
    }


    /**
     * Indicate that a particular ColumnInfo is used by this query.
     */
    @Override
    protected RelationColumn declareField(FieldKey declareKey, QExpr location)
    {
        RelationColumn colTry = _declaredFields.get(declareKey);
        if (colTry != null)
            return colTry;

        List<String> parts = declareKey.getParts();
        QueryRelation table = null;
        FieldKey tableKey = null;
        boolean qualified = true;

        if (parts.size() >= 2)
        {
            tableKey = new FieldKey(null, parts.get(0));
            table = getTable(tableKey);
            if (table != null)
                parts.remove(0);
        }

        if (null == table)
        {
            String name = parts.get(0);
            for (Map.Entry<FieldKey,QueryRelation> e : _tables.entrySet())
            {
                if (null != e.getValue().getColumn(name))
                {
                    if (null != table)
                    {
                        parseError("Ambiguous field: " + name, location);
                        return null;
                    }
                    table = e.getValue();
                    tableKey = e.getKey();
                }
            }
            if (null == table)
            {
                RelationColumn ret = super.declareField(declareKey, location);
                if (null == ret)
                    parseError("Could not resolve column: " + declareKey, location);
                return ret;
            }

            FieldKey fullKey = FieldKey.fromParts(tableKey,declareKey);
            colTry = _declaredFields.get(fullKey);
            if (colTry != null)
            {
                _declaredFields.put(declareKey, colTry);
                return colTry;
            }
            qualified = false;
        }

        FieldKey key = new FieldKey(tableKey, parts.get(0));
        RelationColumn colParent = _declaredFields.get(key);
        if (colParent == null)
        {
            RelationColumn relColumn = table.getColumn(key.getName());
            if (relColumn == null)
            {
                parseError("Unknown field " + key.getDisplayString(), location);
                return null;
            }
            _declaredFields.put(key, relColumn);
            colParent = relColumn;
        }
        for (int i = 1; i < parts.size(); i ++)
        {
            key = new FieldKey(key, parts.get(i));
            RelationColumn nextColumn = _declaredFields.get(key);
            if (nextColumn == null)
            {
                nextColumn = table.getLookupColumn(colParent, key.getName());
                if (nextColumn == null)
                {
                    parseError("Unknown field " + key.getDisplayString(), location);
                    return null;
                }

                _declaredFields.put(key, nextColumn);
            }
            colParent = nextColumn;
        }

        // if table name was not specified, add the actual requested field as well
        if (!qualified)
            _declaredFields.put(declareKey, colParent);
        return colParent;
    }



    private void declareFields(QExpr expr)
    {
        if (expr instanceof QMethodCall)
        {
			assert expr.childList().size() == 1 || expr.childList().size() == 2;
			if (expr.childList().size() == 2)
            	declareFields((QExpr)expr.getLastChild());
            return;
        }
        if (expr instanceof QQuery)
        {
            QueryRelation sub = ((QQuery)expr).getQuerySelect();
            if (sub == null)
            {
                sub = createSubquery((QQuery)expr, false, null);
                ((QQuery)expr)._select = sub;
            }
            sub.declareFields();
            return;
        }
        if (expr instanceof QUnion)
        {
            QueryUnion sub = ((QUnion)expr).getQueryUnion();
            if (sub == null)
            {
                sub = new QueryUnion(this, (QUnion)expr, false, null);
                ((QUnion)expr)._union= sub;
            }
            sub.declareFields();
            return;
        }
        if (expr instanceof QRowStar)
        {
            return;
        }

        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            if (null != resolveParameter(key))
                return;
            RelationColumn column = declareField(key, expr);
            assert null!=column || getParseErrors().size() > 0;
            return;
        }
        for (QNode child : expr.children())
        {
            declareFields((QExpr)child);
        }
    }


    private boolean _declareCalled = false;

    void declareFields()
    {
        if (_declareCalled)
            return;
        _declareCalled = true;

        Set selectAliases = Sets.newCaseInsensitiveHashSet(); 
        if (null != _columns)
        {
            for (SelectColumn column : _columns.values())
            {
                if (null != column.getAlias())
                    selectAliases.add(column.getAlias());
                declareFields(column.getField());
            }
        }
        for (QExpr on : _onExpressions)
        {
            if (on != null)
                declareFields(on);
        }
        if (null != _where)
            for (QNode expr : _where.children())
            {
                declareFields((QExpr)expr);
            }
        if (_groupBy != null)
        {
            for (QNode expr : _groupBy.children())
            {
                declareFields((QExpr)expr);
            }
        }
        if (null != _having)
            for (QNode expr : _having.children())
            {
                declareFields((QExpr)expr);
            }
        if (null != _orderBy)
        {
            for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
            {
                QExpr expr = entry.getKey();
                if (expr instanceof QIdentifier && selectAliases.contains(expr.getTokenText()))
                    continue;
                declareFields(expr);
            }
        }

        // move validation step into Query interface
        validateAfterParse();
    }


    void validateAfterParse()
    {
        if (null != _groupBy)
        {
            for (QNode expr : _groupBy.children())
            {
                if (expr instanceof QExpr && ((QExpr)expr).isConstant())
                    parseError("Expression in Group By clause must not be a constant", expr);
            }
        }
    }

    
    /*
     * Return the result of replacing field names in the expression with QField objects.
     */
    QExpr resolveFields(QExpr expr, @Nullable QNode parent)
    {
        if (expr instanceof QQuery)
        {
            QueryRelation subquery = createSubquery((QQuery) expr, false, null);
            return new QQuery(subquery);
        }
        if (expr instanceof QRowStar)
        {
            return expr;
        }

        FieldKey key = expr.getFieldKey();
        if (key != null)
        {
            if (key.getParent() == null)
            {
                QParameter param = resolveParameter(key);
                if (null != param)
                    return param;
            }
            QField ret = getField(key, expr);
            QueryParseException error = ret.fieldCheck(parent, getSqlDialect());
            if (error != null)
                getParseErrors().add(error);
            return ret;
        }

        // We need to recognize method call identifiers, so we handle
        // unqualified column names that are the same as a built-in method.
        // e.g. SELECT Floor, Floor(1.4) FROM ...
        QExpr methodName = null;
        if (expr instanceof QMethodCall)
        {
            methodName = (QExpr)expr.childList().get(0);
            if (null == methodName.getFieldKey())
                methodName = null;
        }

        QExpr ret = (QExpr) expr.clone();
        for (QNode child : expr.children())
        {
            //
            if (child == methodName)
                ret.appendChild(getField(((QExpr)child).getFieldKey(), expr, true));
            else
                ret.appendChild(resolveFields((QExpr)child, expr));
        }

        QueryParseException error = ret.fieldCheck(parent, getSqlDialect());
        if (error != null)
            getParseErrors().add(error);
        return ret;
    }


    /* NOTE there is only one merged global list of parameters.
     * However, we still want to make sure the parameter is in scope
     * for this particular select
     *
     * declareField should catch the case of a parameter that exists in the global
     * list, but is not in scope for this select.
     */
    QParameter resolveParameter(FieldKey key)
    {
        if (!parametersInScope.contains(key))
            return null;
        return _query.resolveParameter(key);
    }


    public boolean isAggregate()
    {
        if (_groupBy != null)
        {
            return true;
        }
        if (null != _columns)
        {
            for (SelectColumn column : _columns.values())
            {
                if (column.getField().isAggregate())
                    return true;
            }
        }
        return false;
    }


    public boolean hasSubSelect()
    {
        if (_columns == null)
            return false;

        for (SelectColumn column : _columns.values())
        {
            QNode field = column.getField();
            if (field instanceof QQuery)
                return true;
        }
        return false;
    }


    /**
     * Construct a table info which represents this query.
     *
     */
    public QueryTableInfo getTableInfo()
    {
        Set<RelationColumn> set = new HashSet<RelationColumn>(_columns.values());

        resolveFields();
        if (getParseErrors().size() != 0)
            return null;

        getSuggestedColumns(set);

        final SQLFragment sql = _getSql(true);
        if (null == sql)
            return null;

        QueryTableInfo ret = new QueryTableInfo(this, "_select")
        {
            @NotNull
            @Override
            public SQLFragment getFromSQL(String alias)
            {
                SQLFragment f = new SQLFragment();
                f.append("(").append(sql).append(") ").append(alias);
                return f;
            }

            @Override
            public boolean hasSort()
            {
                return _orderBy != null && !_orderBy.childList().isEmpty();
            }
        };

        for (SelectColumn col : _columns.values())
        {
            ColumnInfo aliasedColumn = new RelationColumnInfo(ret, col);
            ret.addColumn(aliasedColumn);
        }
        assert MemTracker.put(ret);
        return ret;
    }


    boolean _resolved = false;

    public void resolveFields()
    {
        if (_resolved)
            return;
        _resolved = true;

        if (getParseErrors().size() != 0)
            return;

        declareFields();
        if (getParseErrors().size() != 0)
            return;

//        SqlDialect dialect = getSqlDialect();

        CaseInsensitiveHashSet aliasSet = new CaseInsensitiveHashSet();

        for (SelectColumn col : _columns.values())
        {
            aliasSet.add(col.getAlias());
            col.getResolvedField();
        }
        if (getParseErrors().size() != 0)
            return;

        for (QJoinOrTable qt : _parsedJoins)
        {
            if (qt instanceof QJoin)
            {
                if (null != ((QJoin)qt)._on)
                    resolveFields(((QJoin)qt)._on, null);
            }
            else if (qt instanceof QTable)
            {
                if (((QTable)qt).getQueryRelation() instanceof QuerySelect)
                    ((QuerySelect)((QTable)qt).getQueryRelation()).resolveFields();
            }
        }

        if (_where != null)
            for (QNode expr : _where.children())
                resolveFields((QExpr)expr, null);

        if (_groupBy != null)
        {
            for (QNode expr : _groupBy.children())
                resolveFields((QExpr)expr, _groupBy);
        }
        if (_having != null)
        {
            for (QNode expr : _having.children())
                resolveFields((QExpr)expr, null);
        }
        if (_orderBy != null)
        {
            for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
            {
                QExpr expr = entry.getKey();
                if (!(expr instanceof QIdentifier && aliasSet.contains(expr.getTokenText())))
                    resolveFields(expr, _orderBy);
            }
        }
    }


    public SQLFragment getSql()
    {
        resolveFields();
        if (getParseErrors().size() != 0)
            return null;

        return _getSql(false);
    }


    public SQLFragment _getSql(boolean selectAll)
    {
        SqlBuilder sql = new SqlBuilder(_schema.getDbSchema());

        if (null == _distinct)
        {
            sql.pushPrefix("SELECT ");
            selectAll = true;
        }
        else
        {
            sql.pushPrefix("SELECT DISTINCT ");
        }

        int count = 0;
        for (SelectColumn col : _columns.values())
            if (col._selected)
                count++;
        if (count == 0)
            selectAll = true;

        // keep track of mapping from source alias to generated alias (used by ORDER BY)
        CaseInsensitiveHashMap<String> aliasMap = new CaseInsensitiveHashMap<String>();
        
        SqlDialect dialect = getSqlDialect();
        for (SelectColumn col : _columns.values())
        {
            if (!selectAll && !col._selected)
                continue;
            String alias = col.getAlias();
            assert null != alias;
            if (alias == null)
            {
                parseError("Column requires alias", col.getField());
                return null;
            }
            sql.append(col.getInternalSql());
            sql.append(" AS ");
            String sqlAlias = dialect.makeLegalIdentifier(alias);
            sql.append(sqlAlias);
            aliasMap.put(alias,sqlAlias);
            sql.nextPrefix(",\n");
            count++;
        }
        if (getParseErrors().size() != 0)
            return null;

        sql.popPrefix();
        sql.pushPrefix("\nFROM ");
        for (QJoinOrTable qt : _parsedJoins)
        {
            qt.appendSql(sql, this);
            sql.nextPrefix(",");
        }
        sql.popPrefix();

        if (_where != null)
        {
            sql.pushPrefix("\nWHERE ");
            for (QNode expr : _where.children())
            {
                sql.append("(");
                QExpr term = resolveFields((QExpr)expr, null);
                term.appendSql(sql);
                sql.append(")");
                sql.nextPrefix(" AND ");
            }
            sql.popPrefix();
        }
        if (_groupBy != null)
        {
            sql.pushPrefix("\nGROUP BY ");
            for (QNode expr : _groupBy.children())
            {
                sql.append("(");
                QExpr gbExpr = resolveFields((QExpr)expr, _groupBy);
                // check here again for constants, after resolveFields()
                if (gbExpr.isConstant())
                    parseError("Expression in Group By clause must not be a constant", expr);
                gbExpr.appendSql(sql);
                sql.append(")");
                sql.nextPrefix(",");
            }
            sql.popPrefix();
        }
        if (_having != null)
        {
            sql.pushPrefix("\nHAVING ");
            for (QNode expr : _having.children())
            {
                sql.append("(");
                QExpr term = resolveFields((QExpr)expr, null);
                term.appendSql(sql);
                sql.append(")");
                sql.nextPrefix(" AND ");
            }
            sql.popPrefix();
        }
        if (_orderBy != null)
        {
            if (_limit == null && !getSqlDialect().allowSortOnSubqueryWithoutLimit())
            {
                parseError("ORDER BY in a LabKey SQL query in this database is not supported unless LIMIT is also specified.  You can set the sort using a custom grid view.", _orderBy);
                return null;
            }
            sql.pushPrefix("\nORDER BY ");
            for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
            {
                QExpr expr = entry.getKey();
                if (expr instanceof QIdentifier && aliasMap.containsKey(expr.getTokenText()))
                {
                    sql.append(aliasMap.get(expr.getTokenText()));
                }
                else
                {
                    QExpr r = resolveFields(expr, _orderBy);
                    r.appendSql(sql);
                }
                if (!entry.getValue().booleanValue())
                    sql.append(" DESC");
                sql.nextPrefix(",");
            }
            sql.popPrefix();
        }
        if (_limit != null)
        {
            getSqlDialect().limitRows(sql, _limit.getLimit());
        }
        if (!getParseErrors().isEmpty())
            return null;

        SqlBuilder ret = sql;

        if (AppProps.getInstance().isDevMode())
        {
            ret = new SqlBuilder(sql.getDialect());
            String comment = "<QuerySelect";
            if (!StringUtils.isEmpty(_savedName))
                comment += " name='" + StringUtils.trimToEmpty(_savedName) + "'";
            comment += ">";
            ret.appendComment(comment);
            if (null != _queryText)
            {
                // Handle any combination of line endings - \n (Unix), \r (Mac), \r\n (Windows), \n\r (nobody)
                for (String s : _queryText.split("(\n\r?)|(\r\n?)"))
                    if (null != StringUtils.trimToNull(s))
                        ret.appendComment("|         " + s);
            }
            ret.append(sql);
            ret.appendComment("</QuerySelect>");
        }
        return ret;
    }


	private void parseError(String message, QNode node)
	{
		Query.parseError(getParseErrors(), message, node);
	}



    public QueryDocument getDesignDocument()
    {
        QueryDocument ret = QueryDocument.Factory.newInstance();
        ret.addNewQuery();
        DgQuery.Select select = ret.getQuery().addNewSelect();
        DgQuery.Where dgWhere = ret.getQuery().addNewWhere();
        DgQuery.OrderBy dgOrderBy = ret.getQuery().addNewOrderBy();
        if (_columns == null)
        {
            return null;
        }
        for (SelectColumn column : _columns.values())
        {
            DgColumn dgColumn = select.addNewColumn();
            if (column.getAlias() != null)
            {
                dgColumn.setAlias(column.getAlias());
            }
            DgValue dgValue = dgColumn.addNewValue();
            QExpr value = column.getField();
            FieldKey key = value.getFieldKey();
            if (key != null)
            {
                dgValue.addNewField().setStringValue(key.toString());
            }
            else
            {
                dgValue.setSql(value.getSourceText());
            }
        }
        if (dgWhere != null)
        {
            if (_where == null)
            {
                _where = new QWhere();
            }
            _where.fillWhere(dgWhere);
        }
        if (_orderBy != null)
        {
            for (Map.Entry<QExpr, Boolean> entry : _orderBy.getSort())
            {
                DgOrderByString dgClause;
                FieldKey field = entry.getKey().getFieldKey();
                if (field != null)
                {
                    dgClause = dgOrderBy.addNewField();
                    dgClause.setStringValue(field.toString());
                }
                else
                {
                    dgClause = dgOrderBy.addNewSql();
                    dgClause.setStringValue(entry.getKey().getSourceText());
                }
                if (entry.getValue().booleanValue())
                {
                    dgClause.setDir("ASC");
                }
                else
                {
                    dgClause.setDir("DESC");
                }
            }
        }
        assert MemTracker.put(ret);
        return ret;
    }


    public void update(DgQuery query, List<QueryException> errors)
    {
        _columns.clear();
        for (DgColumn dgColumn : query.getSelect().getColumnArray())
        {
            QExpr field = null;
            DgValue value = dgColumn.getValue();
            if (value.isSetField())
            {
                field = QIdentifier.of(FieldKey.fromString(value.getField().getStringValue()));
            }
            else if (value.isSetSql())
            {
                field = (new SqlParser()).parseExpr(value.getSql(), errors);
            }
            if (null == field)
                continue;
            SelectColumn column = new SelectColumn(field);
            if (dgColumn.isSetAlias() && dgColumn.getAlias().trim().length() > 0)
            {
                column.setAlias(dgColumn.getAlias());
            }
            _columns.put(new FieldKey(null, column.getAlias()), column);
        }
        if (query.getWhere() != null)
        {
            if (_where == null)
            {
                _where = new QWhere();
            }
            _where.updateWhere(query.getWhere(), errors);
        }
        if (query.getOrderBy() != null)
        {
            if (_orderBy == null)
            {
                _orderBy = new QOrder();
            }
            _orderBy.removeChildren();
            for (DgOrderByString field : query.getOrderBy().getFieldArray())
            {
                _orderBy.addOrderByClause(QIdentifier.of(FieldKey.fromString(field.getStringValue())), !"DESC".equals(field.getDir()));
            }
            for (DgOrderByString expr : query.getOrderBy().getSqlArray())
            {
                _orderBy.addOrderByClause((new SqlParser()).parseExpr(expr.getStringValue(), errors), !"DESC".equals(expr.getDir()));
            }
        }
    }


    int getSelectedColumnCount()
    {
        return _columns.size();
    }


    SelectColumn getColumn(String name)
    {
        FieldKey key = new FieldKey(null,name);
        SelectColumn col = _columns.get(key);
        if (col != null)
            col._selected = true;
        return col;
    }


    protected Map<String,RelationColumn> getAllColumns()
    {
        LinkedHashMap<String,RelationColumn> ret = new LinkedHashMap<String, RelationColumn>(_columns.size()*2);
        for (Map.Entry<FieldKey,SelectColumn> e : _columns.entrySet())
        {
            if (null == e.getKey().getParent())
                ret.put(e.getKey().getName(), e.getValue());
        }
        return ret;
    }


    SelectColumn getLookupColumn(RelationColumn parentRelCol, String name)
    {
        assert parentRelCol instanceof SelectColumn;
        assert parentRelCol.getTable() == QuerySelect.this;

        if (null != QuerySelect.this._distinct || QuerySelect.this.isAggregate())
            return null;

        SelectColumn parent = (SelectColumn)parentRelCol;

        FieldKey key = new FieldKey(parent._key, name);
        SelectColumn c = _columns.get(key);
        if (null != c)
            return c;

        QExpr qexpr = parent.getResolvedField();
        if (!(qexpr instanceof QField))
            return null;

        RelationColumn fromParentColumn = ((QField)qexpr).getRelationColumn();
        if (fromParentColumn == null)
            return null;
        RelationColumn fromLookupColumn = fromParentColumn.getTable().getLookupColumn(fromParentColumn, name);
        if (fromLookupColumn == null)
            return null;
        SelectColumn sc = new SelectColumn(new QField(fromLookupColumn, null));
        sc._key = key;
        sc._selected = true;
        _columns.put(key, sc);
        return sc;
    }


    @Override
    public void setContainerFilter(ContainerFilter containerFilter)
    {
        for (QueryRelation queryRelation : _tables.values())
        {
            queryRelation.setContainerFilter(containerFilter);
        }
    }


    @Override
    public Set<RelationColumn> getSuggestedColumns(Set<RelationColumn> selected)
    { resolveFields();

        if (!getParseErrors().isEmpty())
            return Collections.emptySet();

        if (this.isAggregate() || null != this._distinct)
            return Collections.emptySet();

        MultiHashMap<QueryRelation, RelationColumn> maps = new MultiHashMap<QueryRelation, RelationColumn>();
        Set<RelationColumn> ret = new HashSet<RelationColumn>();

        for (SelectColumn sc : _columns.values())
        {
            if (null == sc._field)
                continue;
            QExpr expr = sc.getResolvedField();
            if (!(expr instanceof QField))
                continue;
            QField field = (QField)expr;
            if (null == field.getTable() || null == field.getRelationColumn())
                continue;
            maps.put(field.getTable(), field.getRelationColumn());
        }

        for (Map.Entry<QueryRelation, Collection<RelationColumn>> e : maps.entrySet())
        {
            IdentityHashMap<RelationColumn,RelationColumn> h = new IdentityHashMap<RelationColumn,RelationColumn>();
            for (RelationColumn rc : e.getValue())
                h.put(rc, rc);
            Set<RelationColumn> suggestedColumns = e.getKey().getSuggestedColumns(h.keySet());
            if (null == suggestedColumns) suggestedColumns = Collections.emptySet();
            for (RelationColumn s : suggestedColumns)
            {
                QField field = new QField(s, null);
                SelectColumn selectColumn = new SelectColumn(field, true);
                this._columns.put(selectColumn.getFieldKey(), selectColumn);
                ret.add(selectColumn);
            }
        }
        return ret;
    }


    RelationColumn getLookupColumn(RelationColumn parentRelCol, ColumnType.Fk fk, String name)
    {
        assert parentRelCol instanceof SelectColumn;
        assert parentRelCol.getTable() == QuerySelect.this;

        if (null != QuerySelect.this._distinct || QuerySelect.this.isAggregate())
            return null;

        SelectColumn parent = (SelectColumn)parentRelCol;

        FieldKey key = new FieldKey(parent._key, name);
        SelectColumn c = _columns.get(key);
        if (null != c)
            return c;

        QExpr qexpr = parent.getResolvedField();
        if (!(qexpr instanceof QField))
            return null;

        RelationColumn fromParentColumn = ((QField)qexpr).getRelationColumn();
        if (fromParentColumn == null)
            return null;
        RelationColumn fromLookupColumn = fromParentColumn.getTable().getLookupColumn(fromParentColumn, fk, name);
        if (fromLookupColumn == null)
            return null;
        SelectColumn col = new SelectColumn(new QField(fromLookupColumn, null));
        col._key = key;
        col._selected = true;
        _columns.put(key, col);
        return col;
    }


    public class SelectColumn extends RelationColumn
    {
        FieldKey _key;
        boolean _selected = false;
        QNode _node;
        QExpr _field;
        QExpr _resolved;
        ColumnInfo _colinfo = null;

        QIdentifier _aliasId;
        String _alias;

        private SelectColumn()
        {
        }

        public SelectColumn(FieldKey fk)
        {
            _field = QFieldKey.of(fk);
            _key = new FieldKey(null, fk.getName());
            _alias = _aliasManager.decideAlias(getName());
        }

        public SelectColumn(QExpr expr, String aliasPrefix)
        {
            _node = expr;
            _field = expr;
            _alias = _aliasManager.decideAlias(aliasPrefix);
            _key = new FieldKey(null,_alias);
        }

        public SelectColumn(QNode node)
        {
            _node = node;
            if (node instanceof QAs)
            {
                _field = ((QAs) node).getExpression();
                FieldKey key = _field.getFieldKey();
                if (null != key && key.getName().equals("*"))
                    parseError("* expression can not be aliased", node);
                _aliasId = ((QAs) node).getAlias();
            }
            else
            {
                _field = (QExpr) node;
                FieldKey fk = _field.getFieldKey();
                if (null != fk && !fk.getName().startsWith("@@"))
                    _key = new FieldKey(null, fk.getName());
            }
            String name = getName();
            if (null != name)
                _alias = _aliasManager.decideAlias(name);
        }

        public SelectColumn(QExpr expr)
        {
            _field = expr;
            FieldKey key = _field.getFieldKey();
            if (null != key)
                _key = new FieldKey(null, key.getName());
            _alias = getName() == null ? null : _aliasManager.decideAlias(getName());
        }

        public SelectColumn(QField field)
        {
            this(field, false);
        }

        public SelectColumn(QField field, boolean generateName)
        {
            _field = field;
            _resolved = field;
            if (generateName)
            {
                _alias = _aliasManager.decideAlias(field.getName());
                _key = new FieldKey(null, _alias);
            }
            else
            {
                _key = new FieldKey(null, field.getName());
                _alias = _aliasManager.decideAlias(getName());
            }
        }

        SQLFragment getInternalSql()
        {
            SqlBuilder b = new SqlBuilder(getDbSchema());
            QExpr expr = getResolvedField();
            expr.appendSql(b);
            return b;
        }

        public String getName()
        {
            if (null != _aliasId)
                return _aliasId.getIdentifier();
            else if (null != _key)
                return _key.getName();
            else
                return null;
        }

        // QuerySelect always returns one part field key, QueryLookupWrapper handles lookups
        @Override public FieldKey getFieldKey()
        {
            return new FieldKey(null, getName());    
        }

        public String getAlias()
        {
            return _alias;
        }

        QueryRelation getTable()
        {
            return QuerySelect.this;
        }

        public JdbcType getJdbcType()
        {
            return null != _resolved ? _resolved.getSqlType() : JdbcType.NULL;
        }


        @Override
        public ForeignKey getFk()
        {
            QExpr expr = getResolvedField();
            if (expr instanceof QField)
                return ((QField)expr).getRelationColumn().getFk();
            return null;
        }


        @Override
        void copyColumnAttributesTo(ColumnInfo to)
        {
            QExpr expr = getResolvedField();
            if (expr instanceof QField)
            {
                ((QField)expr).getRelationColumn().copyColumnAttributesTo(to);
            }
            else
            {
                if (_colinfo == null)
                {
                    _colinfo = expr.createColumnInfo(_subqueryTable, _aliasManager.decideAlias(getAlias()));
                }
                to.copyAttributesFrom(_colinfo);
            }
            if (_aliasId != null)
                to.setLabel(ColumnInfo.labelFromName(getName()));

            // copy URL if possible
            FieldKey fk = null != _resolved ? _resolved.getFieldKey() : null;
            if (null != fk)
                to.copyURLFromStrict(_colinfo, Collections.singletonMap(fk,to.getFieldKey()));

            if (_parsedTables.size() != 1)
                to.setKeyField(false);
        }

        public void appendSource(SourceBuilder builder)
        {
            _field.appendSource(builder);
            if (_aliasId != null)
            {
                builder.append(" AS ");
                _aliasId.appendSource(builder);
            }
        }

        public void setAlias(String alias)
        {
            _alias = StringUtils.trimToNull(alias);
            assert null != _alias;
            _aliasId = new QIdentifier(_alias);
        }

        public QExpr getField()
        {
            return _field;
        }

        public QExpr getResolvedField()
        {
            if (null == _resolved)
                _resolved = resolveFields(getField(), null);
            return _resolved;
        }

        @Override
        public String toString()
        {
            return super.toString() + " alias: " + this.getAlias();
        }
    }
}
