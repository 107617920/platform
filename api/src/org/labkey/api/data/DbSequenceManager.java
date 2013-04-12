package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.data.BaseSelector.ResultSetHandler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 4/6/13
 * Time: 2:23 PM
 */
public class DbSequenceManager
{
    // This handler expects to always return a single integer value
    private static final ResultSetHandler<Integer> INTEGER_RETURNING_HANDLER = new ResultSetHandler<Integer>()
    {
        @Override
        public Integer handle(ResultSet rs, Connection conn) throws SQLException
        {
            rs.next();
            return rs.getInt(1);
        }
    };

    public static DbSequence get(Container c, String name)
    {
        return get(c, name, 0);
    }


    public static DbSequence get(Container c, String name, @NotNull Integer id)
    {
        return new DbSequence(c, ensure(c, name, id));
    }


    private static int ensure(Container c, String name, @NotNull Integer id)
    {
        Integer rowId = getRowId(c, name, id);

        if (null != rowId)
            return rowId.intValue();
        else
            return create(c, name, id);
    }


    private static @Nullable Integer getRowId(Container c, String name, @NotNull Integer id)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment getRowIdSql = new SQLFragment("SELECT RowId FROM ").append(tinfo.getSelectName());
        getRowIdSql.append(" WHERE Container = ? AND Name = ? AND Id = ?");
        getRowIdSql.add(c);
        getRowIdSql.add(name);
        getRowIdSql.add(id);

        SqlSelector selector = new SqlSelector(tinfo.getSchema(), getRowIdSql);

        return selector.getObject(Integer.class);
    }


    // Always initializes to 0; use ensureMinimumValue() to set a higher starting point
    private static int create(Container c, String name, @NotNull Integer id)
    {
        TableInfo tinfo = getTableInfo();

        SQLFragment insertSql = new SQLFragment("INSERT INTO ").append(tinfo.getSelectName());
        insertSql.append(" (Container, Name, Id, Value) VALUES (?, ?, ?, ?)");

        insertSql.add(c);
        insertSql.add(name);
        insertSql.add(id);
        insertSql.add(0);

        tinfo.getSqlDialect().appendSelectAutoIncrement(insertSql, tinfo, "RowId");

        return executeAndReturnInteger(tinfo, insertSql);
    }


    // Not typically needed... CoreContainerListener deletes all the sequences scoped to the container
    public static void delete(Container c, String name)
    {
        delete(c, name, 0);
    }


    // Useful for cases where multiple sequences are needed in a single folder, e.g., a sequence that generates row keys
    // for a list or dataset. Like the other DbSequence operations, the delete executes outside of the current thread
    // transaction; best practice is to commit the full object delete and then (on success) delete the associated sequence.
    public static void delete(Container c, String name, @NotNull Integer id)
    {
        Integer rowId = getRowId(c, name, id);

        if (null != rowId)
        {
            TableInfo tinfo = getTableInfo();
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(tinfo.getSelectName()).append(" WHERE Container = ? AND RowId = ?");
            sql.add(c);
            sql.add(rowId);

            execute(tinfo, sql);
        }
    }


    // Used by container delete
    // TODO: Currently called after all container listeners have successfully deleted... should this be transacted instead?
    public static void deleteAll(Container c)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(tinfo.getSelectName()).append(" WHERE Container = ?");
        sql.add(c);

        execute(tinfo, sql);
    }


    static int current(DbSequence sequence)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment sql = new SQLFragment("SELECT Value FROM ").append(tinfo.getSelectName()).append(" WHERE Container = ? AND RowId = ?");
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());

        DbScope scope = tinfo.getSchema().getScope();

        // Separate connection that's not participating in the current transaction
        try (Connection conn = scope.getPooledConnection())
        {
            return new SqlSelector(scope, conn, sql).getObject(Integer.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    static int next(DbSequence sequence)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo.getSelectName()).append(" SET Value = Value + 1 WHERE Container = ? AND RowId = ?");
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());

        // Reselect the current value
        tinfo.getSqlDialect().addReselect(sql, "Value");

        return executeAndReturnInteger(tinfo, sql);
    }


    // Sets the sequence value to requested minimum, if it's currently less than this value
    static void ensureMinimum(DbSequence sequence, int minimum)
    {
        TableInfo tinfo = getTableInfo();
        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo.getSelectName()).append(" SET Value = ? WHERE Container = ? AND RowId = ? AND Value < ?");
        sql.add(minimum);
        sql.add(sequence.getContainer());
        sql.add(sequence.getRowId());
        sql.add(minimum);

        execute(tinfo, sql);
    }


    private static TableInfo getTableInfo()
    {
        return CoreSchema.getInstance().getTableInfoDbSequences();
    }


    // Executes in a separate connection that does NOT participate in the current transaction
    private static int execute(TableInfo tinfo, SQLFragment sql)
    {
        DbScope scope = tinfo.getSchema().getScope();

        try (Connection conn = scope.getPooledConnection())
        {
            return new SqlExecutor(scope, conn).execute(sql);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    // Executes in a separate connection that does NOT participate in the current transaction
    private static int executeAndReturnInteger(TableInfo tinfo, SQLFragment sql)
    {
        DbScope scope = tinfo.getSchema().getScope();

        try (Connection conn = scope.getPooledConnection())
        {
            return new SqlExecutor(scope, conn).executeWithResults(sql, INTEGER_RETURNING_HANDLER);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public static class TestCase extends Assert
    {
        private static final String NAME = "org.labkey.api.data.DbSequence.Test";

        private DbSequence _sequence;

        @Before
        public void setup()
        {
            Container c = JunitUtil.getTestContainer();

            DbSequenceManager.delete(c, NAME);
            _sequence = DbSequenceManager.get(c, NAME);
        }

        @Test
        public void testBasicOperations()
        {
            assertEquals(0, _sequence.current());
            assertEquals(1, _sequence.next());
            assertEquals(1, _sequence.current());
            assertEquals(2, _sequence.next());
            assertEquals(2, _sequence.current());

            _sequence.ensureMinimum(1000);
            assertEquals(1000, _sequence.current());
            _sequence.ensureMinimum(500);
            assertEquals(1000, _sequence.current());
            _sequence.ensureMinimum(-3);
            assertEquals(1000, _sequence.current());
            assertEquals(1001, _sequence.next());
            assertEquals(1001, _sequence.current());
            _sequence.ensureMinimum(1002);
            assertEquals(1002, _sequence.current());
        }

        @Test
        public void testPerformance()
        {
            final int n = 1000;
            final long start = System.currentTimeMillis();

            for (int i = 0; i < n; i++)
                assertEquals(i + 1, _sequence.next());

            final long elapsed = System.currentTimeMillis() - start;
            final double perSecond = n / (elapsed / 1000.0);

            assertTrue(perSecond > 100);   // A very low bar
        }

        @After
        public void cleanup()
        {
            Container c = JunitUtil.getTestContainer();
            DbSequenceManager.delete(c, NAME);
        }
    }
}
