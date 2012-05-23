/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CachedResultSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.OORDisplayColumnFactory;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.QueryDefinitionImpl;
import org.labkey.query.design.DgQuery;
import org.labkey.query.design.QueryDocument;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.ExceptionUtil.ExceptionInfo.LabkeySQL;
import static org.labkey.api.util.ExceptionUtil.ExceptionInfo.QueryName;
import static org.labkey.api.util.ExceptionUtil.ExceptionInfo.QuerySchema;


/**
 * Query is the "interface" to the SQL->SQL transformation code
 *
 * This class manages the state transitions from
 *
 * parse, resolve, generateSQL
 *
 */

public class Query
{
    String _name = null;
    private final QuerySchema _schema;
	String _querySource;
    boolean _strictColumnList = false;
	private ArrayList<QueryException> _parseErrors = new ArrayList<QueryException>();

    private TablesDocument _metadata = null;
    private ContainerFilter _containerFilter;
    private QueryRelation _queryRoot;
    ArrayList<QParameter> _parameters;

    private Query _parent; // only used to avoid recursion for now

    private int _aliasCounter = 0;

    IdentityHashMap<QueryTable, Map<FieldKey,QueryRelation.RelationColumn>> qtableColumnMaps = new IdentityHashMap<QueryTable, Map<FieldKey,QueryRelation.RelationColumn>>();

    public Query(@NotNull QuerySchema schema)
    {
        _schema = schema;
        assert MemTracker.put(this);
    }

    public Query(@NotNull QuerySchema schema, Query parent)
    {
        _schema = schema;
        _parent = parent;
        if (null != _parent)
            _depth = _parent._depth + 1;
        assert MemTracker.put(this);
    }

    public Query(@NotNull QuerySchema schema, String sql)
    {
        _schema = schema;
        _querySource = sql;
        assert MemTracker.put(this);
    }

    public void setStrictColumnList(boolean b)
    {
        _strictColumnList = b;
    }

    /* for debugging */
    public void setName(String name)
    {
        _name = name;
        if (null != _queryRoot)
            _queryRoot.setSavedName(name);
    }

	QuerySchema getSchema()
	{
        if (null == _schema)
            throw new IllegalStateException("Schema must be set");
		return _schema;
	}


    public final int incrementAliasCounter()
    {
        return ++_aliasCounter;
    }


    public void setTablesDocument(TablesDocument doc)
    {
        _metadata = doc;
    }

    public TablesDocument getTablesDocument()
    {
        return _metadata;
    }

    public void setContainerFilter(ContainerFilter containerFilter)
    {
        _containerFilter = containerFilter;
    }

    public ContainerFilter getContainerFilter()
    {
        return _containerFilter;
    }


    public void parse()
    {
        if (null == _querySource)
            throw new IllegalStateException("SQL has not been specified");

        _parse(_querySource);
        
        for (QueryException e : _parseErrors)
        {
            ExceptionUtil.decorateException(e, LabkeySQL, _querySource, false);
            if (null != getSchema() && null != getSchema().getName())
                ExceptionUtil.decorateException(e, QuerySchema, getSchema().getName(), false);
            if (null != _name)
                ExceptionUtil.decorateException(e, QueryName, _name, false);
        }
    }


    public void parse(String queryText)
    {
        _querySource = queryText;
        parse();
    }


	private void _parse(String queryText)
    {
		try
		{
            SqlParser parser = new SqlParser(getSchema().getDbSchema().getSqlDialect());

            parser.parseQuery(queryText, _parseErrors);
            _parameters = parser.getParameters();

			QNode root = parser.getRoot();
            QueryRelation relation = createQueryRelation(this, root);

            if (relation == null)
                return;

            _queryRoot = relation;

            if (_queryRoot._savedName == null && _name != null)
                _queryRoot.setSavedName(_name);

            if (_parseErrors.isEmpty() && null != _queryRoot)
                _queryRoot.declareFields();
		}
        catch (UnauthorizedException ex)
        {
            throw ex;
        }
		catch (RuntimeException ex)
		{
			throw wrapRuntimeException(ex, _querySource);
		}
    }



    public static QueryRelation createQueryRelation(Query query, QNode root)
    {
        if (root instanceof QUnion)
            return new QueryUnion(query, (QUnion)root);

        if (!(root instanceof QQuery))
            return null;

        QuerySelect select = new QuerySelect(query, (QQuery)root);

        if (null == root.getChildOfType(QPivot.class))
            return select;

        QueryPivot pivot = new QueryPivot(query, select, (QQuery)root);
        pivot.setAlias("_pivot");
        QueryLookupWrapper wrapper = new QueryLookupWrapper(query, pivot, null);

        if (null == ((QQuery) root).getLimit() && null == ((QQuery) root).getOrderBy())
            return wrapper;

        QuerySelect qob = new QuerySelect(wrapper, ((QQuery)root).getOrderBy(), ((QQuery)root).getLimit());
        return qob;
    }



    /**
     * When the user has chosen to create a new query based on a particular table, create a new QueryDef which
     * selects all of the non-hidden columns from that table.
     */
	public void setRootTable(FieldKey key)
	{
		SourceBuilder builder = new SourceBuilder();
		builder.append("SELECT ");
		builder.pushPrefix("");
		QueryRelation relation = resolveTable(getSchema(), null, key, key.getName());
		if (relation == null)
		{
			builder.append("'Table not found' AS message");
		}
		else
		{
            TableInfo table = relation.getTableInfo();
            boolean foundColumn = false;
            if (null == table)
            {
                // can't generate good sql text if the source query can't be parsed
                // we should have an error to display if null==table

                // TODO caller should check and display the parse error
                // instead of returning message in the generated SQL
                assert !this.getParseErrors().isEmpty();
            }
            else
            {
                List<FieldKey> defaultVisibleColumns = table.getDefaultVisibleColumns();
                for (FieldKey field : defaultVisibleColumns)
                {
                    if (field.getParent() != null)
                        continue;
                    assert null != table.getColumn(field.getName());
                    if (null == table.getColumn(field.getName()))
                        continue;
                    List<String> parts = new ArrayList<String>();
                    parts.add(key.getName());
                    parts.addAll(field.getParts());
                    QFieldKey qfield = QFieldKey.of(FieldKey.fromParts(parts));
                    qfield.appendSource(builder);
                    builder.nextPrefix(",");
                    foundColumn = true;

                    // Check if there's a corresponding OORIndicator that's not part of the default set, and add it
                    FieldKey oorFieldKey = new FieldKey(field.getParent(), field.getName() + OORDisplayColumnFactory.OORINDICATOR_COLUMN_SUFFIX);
                    if (table.getColumn(oorFieldKey.getName()) != null && !defaultVisibleColumns.contains(oorFieldKey))
                    {
                        List<String> oorParts = new ArrayList<String>();
                        oorParts.add(key.getName());
                        oorParts.addAll(oorFieldKey.getParts());
                        QFieldKey oorQField = QFieldKey.of(FieldKey.fromParts(oorParts));
                        oorQField.appendSource(builder);
                        builder.nextPrefix(",");
                    }
                }
			}
            if (!foundColumn)
            {
                List<String> pkNames = null==table ? null : table.getPkColumnNames();
                if (null==pkNames || pkNames.isEmpty())
                {
                    builder.append("'No columns selected' AS message");
                }
                else
                {
                    for (String pkName : pkNames)
                    {
                        List<String> parts = new ArrayList<String>();
                        parts.add(key.getName());
                        parts.add(pkName);
                        QFieldKey qfield = QFieldKey.of(FieldKey.fromParts(parts));
                        qfield.appendSource(builder);
                        builder.nextPrefix(",");
                    }
                }
            }
		}
		builder.popPrefix();
		builder.append("\nFROM ");
		QFieldKey.of(new FieldKey(key.getParent(), key.getName())).appendSource(builder);
		if (key.getParent() != null)
		{
			builder.append(" AS ");
			new QIdentifier(key.getName()).appendSource(builder);
		}
		parse(builder.getText());
	}



    public TableInfo getFromTable(FieldKey key)
    {
		return _queryRoot instanceof QuerySelect ? ((QuerySelect)_queryRoot).getFromTable(key) : null;
    }


    public String getQueryText()
    {
        return _queryRoot == null ? null : _queryRoot.getQueryText();
    }


    public Set<FieldKey> getFromTables()
    {
		return isSelect() ? ((QuerySelect)_queryRoot).getFromTables() : null;
    }


    public List<QueryException> getParseErrors()
    {
        return _parseErrors;
    }


    public boolean isAggregate()
    {
		return isSelect() && ((QuerySelect)_queryRoot).isAggregate();
    }


    public boolean isUnion()
    {
        return _queryRoot instanceof QueryUnion;
    }


    public boolean isSelect()
    {
        return _queryRoot instanceof QuerySelect;
    }


    public boolean hasSubSelect()
    {
		return isSelect() && ((QuerySelect)_queryRoot).hasSubSelect();
    }


    private void assertParsed()
    {
        if (_parseErrors.size() == 0 && null == _queryRoot)
        {
            assert false : "call parse() first";
            // shouldn't get here, there should be a parse error if parse() failed
            parseError(getParseErrors(), "Error parsing query", null);
        }
    }


    public ArrayList<QueryService.ParameterDecl> getParameters()
    {
        assertParsed();
        if (_parseErrors.size() > 0)
            return null;
        // don't return hidden parameters
        ArrayList<QueryService.ParameterDecl> ret = new ArrayList<QueryService.ParameterDecl>(_parameters.size());
        for (QParameter p : _parameters)
        {
           if (!p.getName().startsWith("@@"))
               ret.add(p);
        }
        return ret;
    }


    QParameter resolveParameter(FieldKey key)
    {
        if (key.getParent() != null || _parameters == null)
            return null;
        String name = key.getName();
        for (QParameter param : _parameters)
        {
            if (param.getName().equalsIgnoreCase(name))
                return param;
        }
        return null;
    }


    public TableInfo getTableInfo()
    {
        try
        {
            assertParsed();
            if (_parseErrors.size() > 0)
                return null;
            TableInfo tinfo = _queryRoot.getTableInfo();
            if (tinfo instanceof ContainerFilterable && tinfo.supportsContainerFilter() && getContainerFilter() != null)
                ((ContainerFilterable) tinfo).setContainerFilter(getContainerFilter());

            return tinfo;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(Query.class).error("error", x);
            throw Query.wrapRuntimeException(x, _querySource);
        }
    }


    public QueryDocument getDesignDocument()
    {
		return isSelect() ? ((QuerySelect)_queryRoot).getDesignDocument() : null;
    }


    public void update(DgQuery query, List<QueryException> errors)
    {
		if (isSelect())
			((QuerySelect)_queryRoot).update(query, errors);
    }



    static QueryInternalException wrapRuntimeException(RuntimeException ex, String sql)
    {
        if (ex instanceof QueryInternalException)
            return (QueryInternalException)ex;
        else
            return new QueryInternalException(ex, sql);
    }


    static class QueryInternalException extends RuntimeException
    {
        QueryInternalException(RuntimeException cause, String sql)
        {
            super("Internal error while parsing \""+ sql + "\"", cause);
        }
    }


	//
	// Helpers
	//


	static void parseError(List<QueryException> errors, String message, QNode node)
	{
		int line = 0;
		int column = 0;
		if (node != null)
		{
			line = node.getLine();
			column = node.getColumn();
		}
		//noinspection ThrowableInstanceNeverThrown
		errors.add(new QueryParseException(message, null, line, column));
	}



    int _countResolvedTables = 0;
    int _depth = 1;

    private int getTotalCountResolved()
    {
        return _countResolvedTables + (null == _parent ? 0 : _parent.getTotalCountResolved());
    }

	/**
	 * Resolve a particular table name.  The table name may have schema names (folder.schema.table etc.) prepended to it.
	 */
	QueryRelation resolveTable(QuerySchema schema, QNode node, FieldKey key, String alias)
	{
        ++_countResolvedTables;
        if (getTotalCountResolved() > 200 || _depth > 20)
        {
            // recursive query?
            parseError(_parseErrors, "Too many tables used in this query (recursive?)", null);
            return null;
        }

		List<String> parts = key.getParts();
		List<String> names = new ArrayList<String>(parts.size());
		for (String part : parts)
			names.add(FieldKey.decodePart(part));

		for (int i = 0; i < parts.size() - 1; i ++)
		{
			String name = names.get(i);
			schema = schema.getSchema(name);
			if (schema == null)
			{
				parseError(_parseErrors, "Table " + StringUtils.join(names,".") + " not found.", node);
				return null;
			}
		}

        Object t;

        try
        {
            if (schema instanceof UserSchema)
                t  = ((UserSchema)schema)._getTableOrQuery(key.getName(), true, false, _parseErrors);
            else
                t = schema.getTable(key.getName());

            if (t instanceof ContainerFilterable && ((ContainerFilterable)t).supportsContainerFilter() && getContainerFilter() != null)
                ((ContainerFilterable) t).setContainerFilter(getContainerFilter());
        }
        catch (QueryException ex)
        {
            _parseErrors.add(ex);
            return null;
        }
        catch (UnauthorizedException ex)
        {
            parseError(_parseErrors, "No permission to read table: " + key.getName(), node);
            return null;
        }

		if (t == null)
		{
			parseError(_parseErrors, "Table " + StringUtils.join(names,".") + " not found.", node);
			return null;
		}

        if (t instanceof TableInfo)
        {
            return new QueryTable(this, schema, (TableInfo)t, alias);
        }

        if (t instanceof QueryDefinition)
        {
            QueryDefinitionImpl def = (QueryDefinitionImpl)t;
            List<QueryException> tableErrors = new ArrayList<QueryException>();
            Query query = def.getQuery(schema, tableErrors, this);

            if (tableErrors.size() > 0 || query.getParseErrors().size() > 0)
            {
                QueryParseException qpe;
                if (node == null)
                    //noinspection ThrowableInstanceNeverThrown
                    qpe = new QueryParseException("Query '" + key.getName() + "' has errors", null, 0, 0);
                else
                    //noinspection ThrowableInstanceNeverThrown
                    qpe = new QueryParseException("Query '" + key.getName() + "' has errors", null, node.getLine(), node.getColumn());
                ExceptionUtil.decorateException(qpe, ExceptionUtil.ExceptionInfo.ResolveURL, def.urlFor(QueryAction.sourceQuery).getLocalURIString(false), true);
                ExceptionUtil.decorateException(qpe, ExceptionUtil.ExceptionInfo.ResolveText, "edit " + def.getName(), true);
                _parseErrors.add(qpe);
                return null;
            }

            // merge parameter lists
            mergeParameters(query);
            
            QueryRelation ret = query._queryRoot;
            ret.setQuery(this);
            this.getParseErrors().addAll(query.getParseErrors());

            if (query.getTablesDocument() != null && query.getTablesDocument().getTables().getTableArray().length > 0)
            {
                TableType tableType = query.getTablesDocument().getTables().getTableArray(0);
                ret = new QueryLookupWrapper(this, query._queryRoot, tableType);
            }

            ret.setAlias(alias);
            return ret;
        }

		return null;
	}


    void mergeParameters(Query fromQuery)
    {
        CaseInsensitiveHashMap<QParameter> map = new CaseInsensitiveHashMap<QParameter>();
        if (null == _parameters)
            _parameters = new ArrayList<QParameter>();
        for (QParameter p : _parameters)
            map.put(p.getName(),p);
        if (null != fromQuery._parameters)
        {
            for (QParameter p : fromQuery._parameters)
            {
                QParameter to = map.get(p.getName());
                if (null == to)
                    _parameters.add(p);
                else if (to.getType() != p.getType())
                    parseError(_parseErrors, "Parameter is declared with different types: " + p.getName(), to);
            }
        }
    }


	//
	// TESTING
	//
    private static class TestDataLoader extends DataLoader
    {
        private static final String[] COLUMNS = new String[] {"d", "seven", "twelve", "day", "month", "date", "duration", "guid", "createdby", "created"};
        private static final String[] TYPES = new String[] {"double", "int", "int", "string", "string", "dateTime", "string", "string", "int", "dateTime"};
        private static final String[] days = new String[] {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        private static final String[] months = new String[] {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

        private String[][] data;
        private ArrayListMap<String, Object> templateRow = new ArrayListMap<String, Object>();

		// UNDONE: need some NULLS in here
        @SuppressWarnings({"UnusedAssignment"})
        TestDataLoader(String propertyPrefix, int len)
        {
            String now = DateUtil.toISO(new Date());
            data = new String[len+1][];
            data[0] = COLUMNS;
            for (String c : data[0])
                templateRow.put(propertyPrefix + "#" + c, c);
            for (int i=1 ; i<=len ; i++)
            {
                String[] row = data[i] = new String[COLUMNS.length];
                int c = 0;
                row[c++] = "" + Math.exp(i);
                row[c++] = "" + (i%7);
                row[c++] = "" + (i%12);
                row[c++] = days[i%7];
                row[c++] = months[i%12];
                row[c++] = DateUtil.toISO(DateUtil.parseDateTime("2010-01-01") + ((long)i)*12*60*60*1000L);
                row[c++] = DateUtil.formatDuration(i*1000);
                row[c++] = GUID.makeGUID();
                row[c++] = "" + TestContext.get().getUser().getUserId();
                row[c++] = now;
            }

//            for (String[] row : data) System.err.println(StringUtils.join(row,"\t")); System.err.flush();
        }

        public String[][] getFirstNLines(int n) throws IOException
        {
            return data;
        }

        int i=1;

        public CloseableIterator<Map<String, Object>> iterator()
        {
            return new _Iterator();
        }

        class _Iterator implements CloseableIterator<Map<String, Object>>
        {
            public boolean hasNext()
            {
                return i < data.length;
            }

            public Map<String, Object> next()
            {
                return new ArrayListMap<String, Object>(templateRow, Arrays.asList((Object[])data[i++]));
            }

            public void remove()
            {
                throw new UnsupportedOperationException();
            }

            public void close() throws IOException
            {
            }
        }

        public void close()
        {
        }
    }


    static class SqlTest
    {
		public String name = null;
        public String sql;
		public String metadata = null;
        public int countColumns = -1;
        public int countRows = -1;

        SqlTest(String sql)
        {
            this.sql = sql;
        }

        SqlTest(String sql, int cols, int rows)
        {
            this.sql = sql;
            countColumns = cols;
            countRows = rows;
        }


		SqlTest(String name, String sql, String metadata, int cols, int rows)
		{
			this.name = name;
			this.sql = sql;
			this.metadata = metadata;
			countColumns = cols;
			countRows = rows;
		}

        void validate(QueryTestCase test, @Nullable Container container)
        {
            if (null == container)
                container = JunitUtil.getTestContainer();

            CachedResultSet rs = null;

            try
            {
                rs = test.resultset(sql, container==JunitUtil.getTestContainer()?null:container);
                ResultSetMetaData md = rs.getMetaData();
                if (countColumns >= 0)
                    QueryTestCase.assertEquals(sql, countColumns, md.getColumnCount());
                if (countRows >= 0)
                    QueryTestCase.assertEquals(sql, countRows, rs.getSize());

				if (name != null)
				{
                    User user = TestContext.get().getUser();
                    QueryDefinition existing = QueryService.get().getQueryDef(user, container, "lists", name);
                    if (null != existing)
                        existing.delete(TestContext.get().getUser());
					QueryDefinition q = QueryService.get().createQueryDef(user, container, "lists", name);
					q.setSql(sql);
					if (null != metadata)
						q.setMetadataXml(metadata);
                    q.setCanInherit(true);
					q.save(TestContext.get().getUser(), container);
				}
            }
            catch (Exception x)
            {
                QueryTestCase.fail(x.toString() + "\n" + sql);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
    }


    static class FailTest extends SqlTest
    {
        FailTest(String sql)
        {
            super(sql);
        }

        @Override
        void validate(QueryTestCase test, @Nullable Container container)
        {
            CachedResultSet rs = null;

            try
            {
                rs = (CachedResultSet)QueryService.get().select(test.lists, sql);
                QueryTestCase.fail("should fail: " + sql);
            }
            catch (SQLException x)
            {
                // should fail with SQLException not runtime exception
            }
            catch (QueryParseException x)
            {
                // OK
            }
            catch (Exception x)
            {
                QueryTestCase.fail("unexpected exception: " + x.toString());
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
    }


    static int Rcolumns = TestDataLoader.COLUMNS.length + 2; // rowid, entityid
	static int Rsize = 84;
	static int Ssize = 84;

    static SqlTest[] tests = new SqlTest[]
    {
        new SqlTest("SELECT R.seven FROM R UNION SELECT S.seven FROM Folder.qtest.lists.S S", 1, 7),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT S.seven FROM Folder.qtest.lists.S S", 1, Rsize*2),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S", 2, 14),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R", 2, 26),
        new SqlTest("(SELECT 'R' as x, R.seven FROM R) UNION (SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R)", 2, 26),

        // mixed UNION, UNION ALL
        new SqlTest("SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("(SELECT R.seven FROM R UNION SELECT R.seven FROM R) UNION ALL SELECT R.twelve FROM R", 1, 7 + Rsize),
        new SqlTest("(SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R) UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R UNION ALL SELECT R.twelve FROM R", 1, 3*Rsize),
        new SqlTest("SELECT u.seven FROM (SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R) u WHERE u.seven > 5", 1, 6),

        new SqlTest("SELECT d, seven, twelve, day, month, date, duration, guid FROM R", 8, Rsize),
        new SqlTest("SELECT d, seven, twelve, day, month, date, duration, guid FROM lists.R", 8, Rsize),
        new SqlTest("SELECT d, seven, twelve, day, month, date, duration, guid FROM Folder.qtest.lists.S", 8, Rsize),
        new SqlTest("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid FROM R", 8, Rsize),
        new SqlTest("SELECT R.* FROM R", Rcolumns, Rsize),
        new SqlTest("SELECT * FROM R", Rcolumns, Rsize),
        new SqlTest("SELECT R.d, seven, R.twelve AS TWE, R.day DOM, LCASE(GUID) FROM lists.R", 5, Rsize),
        new SqlTest("SELECT true as T, false as F FROM R", 2, Rsize),
        new SqlTest("SELECT COUNT(*) AS _count FROM R", 1, 1),
        new SqlTest("SELECT R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid, R.created, R.createdby, R.createdby.displayname FROM R", 11, Rsize),
        new SqlTest("SELECT R.duration AS elapsed FROM R WHERE R.rowid=1", 1, 1),
		new SqlTest("SELECT R.rowid, R.seven, R.day FROM R WHERE R.day LIKE '%ues%'", 3, 12),
		new SqlTest("SELECT R.rowid, R.twelve, R.month FROM R WHERE R.month BETWEEN 'L' and 'O'", 3, 3*7), // March, May, Nov
        new SqlTest("SELECT R.rowid, R.twelve, (SELECT S.month FROM Folder.qtest.lists.S S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday'", 3, 12),
        new SqlTest("SELECT T.R, T.T, T.M FROM (SELECT R.rowid as R, R.twelve as T, (SELECT S.month FROM Folder.qtest.lists.S S WHERE S.rowid=R.rowid) as M FROM R WHERE R.day='Monday') T", 3, 12),
		new SqlTest("SELECT R.rowid, R.twelve FROM R WHERE R.seven in (SELECT S.seven FROM Folder.qtest.lists.S S WHERE S.seven in (1,4))", 2, Rsize*2/7),

		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S inner join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R AS S left join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S left outer join R AS T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S right join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S right outer join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S full join R T on S.rowid=T.rowid"),
		new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S full outer join R T on S.rowid=T.rowid"),
        new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S cross join R T WHERE S.rowid=T.rowid"),
        new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S, R T WHERE S.rowid=T.rowid"),
        new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S LEFT JOIN (R T INNER JOIN R AS U ON T.rowid=U.rowid) ON S.rowid=t.rowid"),
        new SqlTest("SELECT S.rowid AS Srow, T.rowid AS Trow FROM R S, R AS T INNER JOIN R U ON T.rowid=U.rowid WHERE S.rowid=T.rowid"),

        new SqlTest("SELECT R.seven FROM R UNION SELECT S.seven FROM Folder.qtest.lists.S S", 1, 7),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT S.seven FROM Folder.qtest.lists.S S", 1, Rsize*2),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S", 2, 14),
        new SqlTest("SELECT 'R' as x, R.seven FROM R UNION SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R", 2, 26),
		new SqlTest("(SELECT 'R' as x, R.seven FROM R) UNION (SELECT 'S' as x, S.seven FROM Folder.qtest.lists.S S UNION SELECT 'T' as t, R.twelve FROM R)", 2, 26),
		// mixed UNION, UNION ALL
        new SqlTest("SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("(SELECT R.seven FROM R UNION SELECT R.seven FROM R) UNION ALL SELECT R.twelve FROM R", 1, 7 + Rsize),
        new SqlTest("(SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R) UNION SELECT R.twelve FROM R", 1, 12),
        new SqlTest("SELECT R.seven FROM R UNION ALL SELECT R.seven FROM R UNION ALL SELECT R.twelve FROM R", 1, 3*Rsize),
        new SqlTest("SELECT u.seven FROM (SELECT R.seven FROM R UNION SELECT R.seven FROM R UNION SELECT R.twelve FROM R) u WHERE u.seven > 5", 1, 6),
		// LIMIT
		new SqlTest("SELECT R.day, R.month, R.date FROM R LIMIT 5", 3, 5),
		new SqlTest("SELECT R.day, R.month, R.date FROM R ORDER BY R.date LIMIT 5", 3, 5),

        // quoted identifiers
        new SqlTest("SELECT T.\"count\", T.\"Opened By\", T.Seven, T.MonthName FROM (SELECT R.d as \"count\", R.seven as \"Seven\", R.twelve, R.day, R.month, R.date, R.duration, R.guid, R.created, R.createdby as \"Opened By\", R.month as MonthName FROM R) T", 4, Rsize),

        // PIVOT
        new SqlTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven", 9, 12),
        new SqlTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0 AS ZERO, 1 ONE, 2 AS TWO, 3 THREE, 4 FOUR, 5 FIVE, 6 SIX, NULL AS UNKNOWN)", 10, 12),
        new SqlTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0,1,2,3,4,5,6) ORDER BY twelve LIMIT 4", 9, 4),
        new SqlTest("SELECT seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0,1,2,3,4,5,6) ORDER BY \"0::C\" LIMIT 12", 9, 12),
        new SqlTest("SELECT seven, month, count(*) C\n" +
                "FROM R\n" +
                "GROUP BY seven, month\n" +
                "PIVOT C BY month IN('January','February','March','April','May','June','July','August','September','October','November','December')"),

        // saved queries
        new SqlTest("Rquery",
                "SELECT R.rowid, R.rowid*2 as rowid2, R.d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.guid FROM R",
                "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
                "<table tableName=\"Rquery\" tableDbType=\"NOT_IN_DB\">\n" +
                "<columns>\n" +
                " <column columnName=\"rowid\">\n" +
                "  <fk>\n" +
                "  <fkTable>Squery</fkTable>\n" +
                "  <fkColumnName>rowid</fkColumnName>\n" +
                "  </fk>\n" +
                " </column>\n" +
                " <column columnName=\"rowid2\">\n" +
                "  <fk>\n" +
                "  <fkTable>Squery</fkTable>\n" +
                "  <fkColumnName>rowid</fkColumnName>\n" +
                "  </fk>\n" +
                " </column>\n" +
                "</columns>\n" +
                "</table>\n" +
                "</tables>",
                10, Rsize),
            new SqlTest("SELECT Rquery.d FROM Rquery", 1, Rsize),

            new SqlTest("Squery",
                    "SELECT S.rowid, S.d, S.seven, S.twelve, S.day, S.month, S.date, S.duration, S.guid FROM Folder.qtest.lists.S S",
                    null,
                    9, Rsize),
            new SqlTest("SELECT S.rowid, S.d FROM Squery S", 2, Rsize),

            new SqlTest("SELECT Rquery.rowid, Rquery.rowid.duration FROM Rquery", 2, Rsize),
            new SqlTest("SELECT Rquery.rowid2, Rquery.rowid2.duration FROM Rquery", 2, Rsize),
            new SqlTest("SELECT Rquery.rowid, Rquery.rowid.date, Rquery.rowid2, Rquery.rowid2.duration FROM Rquery", 4, Rsize),

            // NOTE: DISTINCT means lookups can not be pushed down
            new SqlTest("Rdistinct", "SELECT DISTINCT R.twelve FROM R",
                    "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
                    "<table tableName=\"Rquery\" tableDbType=\"NOT_IN_DB\">\n" +
                    "<columns>\n" +
                    " <column columnName=\"twelve\">\n" +
                    "  <fk>\n" +
                    "  <fkTable>Squery</fkTable>\n" +
                    "  <fkColumnName>rowid</fkColumnName>\n" +
                    "  </fk>\n" +
                    " </column>\n" +
                    "</columns>\n" +
                    "</table>\n" +
                    "</tables>",
                    1, 12),
        new SqlTest("SELECT Rdistinct.twelve, Rdistinct.twelve.duration from Rdistinct", 2, 12),

        // GROUPING
        new SqlTest("SELECT R.seven, MAX(R.twelve) AS _max FROM R GROUP BY R.seven", 2, 7),
        new SqlTest("SELECT COUNT(R.rowid) as _count FROM R", 1, 1),
        new SqlTest("SELECT R.seven, MAX(R.twelve) AS _max FROM R GROUP BY R.seven HAVING SUM(R.twelve) > 5", 2, 7),

        // METHODS
        new SqlTest("SELECT ROUND(R.d) AS _d, ROUND(R.d,1) AS _rnd, ROUND(3.1415,2) AS _pi, CONVERT(R.d,SQL_VARCHAR) AS _str FROM R", 4, Rsize),

        // LIMIT
        new SqlTest("SELECT R.day, R.month, R.date FROM R LIMIT 10", 3, 10),
        new SqlTest("SELECT R.day, R.month, R.date FROM R ORDER BY R.date LIMIT 10", 3, 10),
        new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R LIMIT 5", 3, 5),
        new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R ORDER BY date LIMIT 5", 3, 5)
    };

	static SqlTest[] postgres = new SqlTest[]
	{
		// ORDER BY tests
		new SqlTest("SELECT R.day, R.month, R.date FROM R ORDER BY R.date", 3, Rsize),
        new SqlTest("SELECT R.day, R.month, R.date FROM R UNION SELECT R.day, R.month, R.date FROM R ORDER BY date"),
        new SqlTest("SELECT R.guid FROM R WHERE overlaps(CAST('2001-01-01' AS DATE),CAST('2001-01-10' AS DATE),CAST('2001-01-05' AS DATE),CAST('2001-01-15' AS DATE))", 1, Rsize)
    };

	static SqlTest[] negative = new SqlTest[]
	{
		new FailTest("SELECT S.d, S.seven FROM S"),
		new FailTest("SELECT S.d, S.seven FROM Folder.S"),
		new FailTest("SELECT S.d, S.seven FROM Folder.qtest.S"),
		new FailTest("SELECT S.d, S.seven FROM Folder.qtest.list.S"),
        new FailTest("SELECT R.seven, MAX(R.twelve) AS _max FROM R HAVING SUM(R.twelve) > 5"),
        new FailTest("SELECT * FROM R A inner join R B ON 1=1"),            // ambiguous columns
        new FailTest("SELECT SUM(*) FROM R"),
        new FailTest("SELECT d FROM R A inner join R B on 1=1"),            // ambiguous
        new FailTest("SELECT A.*, B.* FROM R A inner join R B on 1=1"),     // ambiguous
        new FailTest("SELECT R.d, seven FROM lists.R A"),                    // R is hidden
        new FailTest("SELECT A.d, B.d FROM lists.R A INNER JOIN lists.R B"),     // ON expected
        new FailTest("SELECT A.d, B.d FROM lists.R A CROSS JOIN lists.R B ON A.d = B.d"),     // ON unexpected

        // UNDONE: should work since R.seven and seven are the same
        new FailTest("SELECT R.seven, twelve, COUNT(*) as C FROM R GROUP BY seven, twelve PIVOT C BY seven IN (0,1,2,3,4,5,6)"),
	};



    public static class QueryTestCase extends Assert
    {
        private String hash = GUID.makeHash();
        private QuerySchema lists;

		Container getSubfolder()
		{
            return ContainerManager.ensureContainer(JunitUtil.getTestContainer().getPath() + "/qtest");
		}


        private void addProperties(ListDefinition l)
        {
            Domain d = l.getDomain();
            for (int i=0 ; i<TestDataLoader.COLUMNS.length ; i++)
            {
                DomainProperty p = d.addProperty();
                p.setPropertyURI(d.getName() + hash + "#" + TestDataLoader.COLUMNS[i]);
                p.setName(TestDataLoader.COLUMNS[i]);
                p.setRangeURI(TestDataLoader.TYPES[i]);
                if ("createdby".equals(TestDataLoader.COLUMNS[i]))
                {
                    p.setLookup(new Lookup(l.getContainer(), "core", "SiteUsers"));
                }
            }
        }

        @Before
        public void setUp() throws Exception
        {
//            _setUp();
        }


        protected void _setUp() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
			Container qtest = getSubfolder();
            ListService.Interface s = ListService.get();

            ListDefinition R = s.createList(c, "R");
            R.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
            R.setKeyName("rowid");
            addProperties(R);
            R.save(user);
            R.insertListItems(user, new TestDataLoader(R.getName() + hash, Rsize), null, null);

            ListDefinition S = s.createList(qtest, "S");
            S.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
            S.setKeyName("rowid");
            addProperties(S);
            S.save(user);
            S.insertListItems(user, new TestDataLoader(S.getName() + hash, Ssize), null, null);

            if (0==1)
            {
                try
                {
                    ListDefinition RHOME = s.createList(ContainerManager.getForPath("/home"), "R");
                    RHOME.setKeyType(ListDefinition.KeyType.AutoIncrementInteger);
                    RHOME.setKeyName("rowid");
                    addProperties(RHOME);
                    RHOME.save(user);
                    RHOME.insertListItems(user, new TestDataLoader(RHOME.getName() + hash, Rsize), null, null);
                } catch (Exception x){};
            }
        }


		@After
        public void tearDown() throws Exception
        {
//            _tearDown();
        }


        protected void _tearDown() throws Exception
        {
            User user = TestContext.get().getUser();

            for (SqlTest test : tests)
            {
                if (test.name != null)
                {
                    QueryDefinition q = QueryService.get().getQueryDef(user, JunitUtil.getTestContainer(), "lists", test.name);
                    if (null != q)
                        q.delete(user);
                }
            }

			ListService.Interface s = ListService.get();

			Container c = JunitUtil.getTestContainer();
			{
				Map<String,ListDefinition> m = s.getLists(c);
				if (m.containsKey("R"))
					m.get("R").delete(user);
				if (m.containsKey("S"))
					m.get("S").delete(user);
			}

			Container qtest = getSubfolder();
			{
				Map<String,ListDefinition> m = s.getLists(qtest);
				if (m.containsKey("S"))
					m.get("S").delete(user);
			}
        }


        @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
        CachedResultSet resultset(String sql, @Nullable Container container) throws Exception
        {
            QuerySchema schema = lists;
            if (null != container)
                schema = schema.getSchema("Folder").getSchema(container.getPath()).getSchema("lists");

			try
			{
				CachedResultSet rs = (CachedResultSet)QueryService.get().select(schema, sql);
				assertNotNull(sql, rs);
				return rs;
			}
			catch (QueryParseException x)
			{
				fail(x.getMessage() + "\n" + sql);
				return null;
			}
			catch (SQLException x)
			{
				fail(x.getMessage() + "\n" + sql);
				return null;
			}
        }


        @Test
        public void testSQL() throws Exception
        {
            // note getPrimarySchema() will return NULL if there are no lists yet
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();

            lists = DefaultSchema.get(user, c).getSchema("lists");
            if (1==1 || null == lists)
            {
                _tearDown();
                _setUp();
                lists = DefaultSchema.get(user, c).getSchema("lists");
            }

            assertNotNull(lists);
            assertNotNull(lists);
            TableInfo Rinfo = lists.getTable("R");
            assertNotNull(Rinfo);
			TableInfo Sinfo = DefaultSchema.get(user, getSubfolder()).getSchema("lists").getTable("S");
            assertNotNull(Sinfo);

            // custom tests
            SqlDialect dialect = lists.getDbSchema().getSqlDialect();
            String sql = "SELECT d, R.seven, R.twelve, R.day, R.month, R.date, R.duration, R.created, R.createdby FROM R";
            CachedResultSet rs = null;


            try
            {
                rs = resultset(sql, null);
                ResultSetMetaData md = rs.getMetaData();
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("d", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("seven", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("twelve", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("day", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("month", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("date", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("created", dialect)));
                assertTrue(sql, 0 < rs.findColumn(AliasManager.makeLegalName("createdby", dialect)));
                assertEquals(sql, 9, md.getColumnCount());
                assertEquals(sql, Rsize, rs.getSize());
                rs.next();

                for (int col=1; col<=md.getColumnCount() ; col++)
                    assertNotNull(sql, rs.getObject(col));
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            // simple tests
            for (SqlTest test : tests)
            {
                test.validate(this, null);
            }

			if (dialect.allowSortOnSubqueryWithoutLimit())
			{
				for (SqlTest test : postgres)
                {
					test.validate(this, null);
                }
			}

			for (SqlTest test : negative)
			{
				test.validate(this, null);
			}

            for (SqlTest test : tests)
            {
                if (test.name != null)
                {
                    QueryDefinition q = QueryService.get().getQueryDef(user, JunitUtil.getTestContainer(), "lists", test.name);
                    assertNotNull(q);
//                    q.delete(user);
                }
            }
        }

        @Test
        public void testContainerFilter() throws Exception
        {
            User user = TestContext.get().getUser();
            Container c = JunitUtil.getTestContainer();
            Container sub = getSubfolder();
            ResultSet rs = null;

            lists = DefaultSchema.get(user, c).getSchema("lists");
            if (1==1 || null == lists)
            {
                _tearDown();
                _setUp();
                lists = DefaultSchema.get(user, c).getSchema("lists");
            }

            {
            QueryDefinition q = QueryService.get().getQueryDef(user, JunitUtil.getTestContainer(), "lists", "QThisContainer");
            if (null != q)
                q.delete(user);
            }

            try
            {
                //
                // test default container filter with inherited query
                //
                SqlTest createQ = new SqlTest("QThisContainer", "SELECT Name, ID FROM core.Containers", null, 2, 1);
                createQ.validate(this, c);
                SqlTest selectQ = new SqlTest("SELECT * FROM QThisContainer");
                selectQ.validate(this, c);
                selectQ.validate(this, sub);

                rs = resultset(selectQ.sql, c);
                boolean hasNext = rs.next();
                assert hasNext;
                assertEquals(rs.getInt(2), c.getRowId());
                ResultSetUtil.close(rs); rs = null;

                rs = resultset(selectQ.sql, sub);
                hasNext = rs.next();
                assert hasNext;
                assertEquals(rs.getInt(2), sub.getRowId());
                ResultSetUtil.close(rs); rs = null;

                //
                // can you think of more good tests
                //
            }
            finally
            {
                QueryDefinition q = QueryService.get().getQueryDef(user, JunitUtil.getTestContainer(), "lists", "QThisContainer");
                if (null != q)
                    q.delete(user);
                ResultSetUtil.close(rs);
            }
        }
    }
}
