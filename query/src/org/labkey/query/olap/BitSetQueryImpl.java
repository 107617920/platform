package org.labkey.query.olap;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ResultSetUtil;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.OlapException;
import org.olap4j.OlapStatement;
import org.olap4j.Position;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Measure;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.Property;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.labkey.query.olap.QubeQuery.OP;

/**
 * Created by matthew on 3/13/14.
 *
 */
public class BitSetQueryImpl
{
    static Logger _log = Logger.getLogger(BitSetQueryImpl.class);

    final Cube cube;
    final HashMap<String,Level> levelMap = new HashMap<>();
    final QubeQuery qq;
    final BindException errors;
    MeasureDef measure;
    OlapConnection connection;
    final String cachePrefix;


    public BitSetQueryImpl(Container c, OlapSchemaDescriptor sd, OlapConnection connection, QubeQuery qq, BindException errors) throws OlapException
    {
        this.connection = connection;
        this.cube = qq.getCube();
        this.qq = qq;
        this.errors = errors;
        this.cachePrefix = "" + c.getRowId() + "/" + sd.getId() + "/";

        initCube();
    }


    void initCube() throws OlapException
    {
        // Member ordinals may not be set, which is really annoying
        for (Hierarchy h : cube.getHierarchies())
        {
            for (Level l : h.getLevels())
            {
                levelMap.put(l.getUniqueName(),l);
                List<Member> members = l.getMembers();
                int count = members.size();
                for (int i=0 ; i<count ; i++)
                {
                    Member m = members.get(i);
                    m.setProperty(Property.StandardMemberProperty.MEMBER_ORDINAL,String.valueOf(i));
                    assert m.getOrdinal() == i;
                }
            }
        }
    }


    abstract class EvalNode
    {
        final Type type;
        EvalNode(Type t)
        {
            this.type = t;
        }
    }

    abstract class Result extends EvalNode
    {
        Result(Type t)
        {
            super(t);
        }
    }


    class MemberSetResult extends Result
    {
        MemberSetResult(Set<Member> members)
        {
            super(Type.setOfMembers);
            this.level = null;
            this.hierarchy = null;
            if (members instanceof MemberSet)
            {
                this.members = (MemberSet) members;
                this.member = null;
            }
            else if (members.size() == 1)
            {
                this.members = null;
                this.member = members.toArray(new Member[1])[0];
            }
            else
            {
                this.member = null;
                this.members = new MemberSet(members);
            }
        }

    /*
        MemberSetResult(Member m)
        {
            super(Type.setOfMembers);
            this.members = null;
            this.level = null;
            this.hierarchy = null;
            this.member = m;
        }
    */

        MemberSetResult(Level level)
        {
            super(Type.setOfMembers);
            this.members = null;
            this.level = level;
            this.hierarchy = null;
            this.member = null;
        }

        MemberSetResult(Hierarchy h)
        {
            super(Type.setOfMembers);
            this.members = null;
            this.level = null;
            this.hierarchy = h;
            this.member = null;
        }

        void toMdxSet(StringBuilder sb)
        {
            if (null != member)
            {
                sb.append("{");
                sb.append(member.getUniqueName());
                sb.append("}\n");
            }
            else if (null != level)
            {
                sb.append(level.getUniqueName()).append(".members");
            }
            else if (null != hierarchy)
            {
                sb.append(hierarchy.getUniqueName()).append(".members");
            }
            else
            {
                sb.append("{");
                String comma = "";
                for (Member m : members)
                {
                    sb.append(comma);
                    sb.append(m.getUniqueName());
                    comma = ",";
                }
                sb.append("}\n");
            }
        }

        Level getLevel()
        {
            if (null != member)
                return member.getLevel();
            if (null != level)
                return level;
            if (null != hierarchy)
                return null;
            if (null != members)
                return members.getLevel();
            return null;
        }

        Hierarchy getHierarchy()
        {
            if (null != member)
                return member.getHierarchy();
            if (null != level)
                return level.getHierarchy();
            if (null != hierarchy)
                return hierarchy;
            if (null != members)
                return members.getHierarchy();
            return null;
        }


        @NotNull
        Collection<Member> getCollection() throws OlapException
        {
            if (null != member)
            {
                return Collections.singletonList(member);
            }
            else if (null != level)
            {
                return level.getMembers();
            }
            else if (null != hierarchy)
            {
                List<Member> ret = new ArrayList<>();
                for (Member r : hierarchy.getRootMembers())
                    addMemberAndChildren(r,ret);
                return ret;
            }
            else
            {
                return members;
            }
        }


 /*
        void addMembersTo(Set<Member> output) throws OlapException
        {
            if (null != level)
            {
                output.addAll(level.getMembers());
            }
            else if (null != members)
            {
                output.addAll(members);
            }
            else if (null != hierarchy)
            {
                for (Level level : hierarchy.getLevels())
                    output.addAll(level.getMembers());
            }
            else
                throw new IllegalStateException();
        }
 */

        final Level level;              // all members of a level
        final Hierarchy hierarchy;      // all members of a hierarchy
        final Member member;            // special case, one member (used in filters)
        final MemberSet members;        // explicit list of members

        @Override
        public String toString()
        {
            Hierarchy h = null;
            Level l = null;
            int count = -1;
            if (null != level)
            {
                h = level.getHierarchy();
                l = level;
                try {count = level.getMembers().size();} catch (OlapException e){/* */}
            }
            else if (null != hierarchy)
            {
                h = hierarchy;
            }
            else if (null != members)
            {
                h = members.getHierarchy();
                l = members.getLevel();
                count = members.size();
            }
            return "MemberSet " +
                    (null != l ? l.getUniqueName() : (null != h ? h.getUniqueName() : "")) +
                    (count == -1 ? "" : " " + String.valueOf(count));
        }
    }


    class CrossResult extends Result
    {
        List<MemberSetResult> results = new ArrayList<>();

        CrossResult()
        {
            super(Type.crossSet);
        }
    }



    void addMemberAndChildren(Member m, Collection<Member> list) throws OlapException
    {
        list.add(m);
        for (Member c : m.getChildMembers())
            addMemberAndChildren(c, list);
    }


    void addMemberAndChildrenInLevel(Member m, Level filter, Collection<Member> list) throws OlapException
    {
        if (m.getLevel().getUniqueName().equals(filter.getUniqueName()))
        {
            list.add(m);
            return;
        }
        for (Member c : m.getChildMembers())
            addMemberAndChildrenInLevel(c, filter, list);
    }



    // there are different types of data that can be manipulated by this "query language" to use
    // the term loosely.
    enum Type
    {
        countOfMembers,     // integer
        member,             // individual categorical value
        setOfMembers,       // set of categorical values
        tupleMemberCount,   // (member, count)
        tupleMemberMembers,  // (member, {members})
        setOfTuples,         // {(member,count),(member,count)}
        crossSet             // {{member}}, 0-1 memberset per hierarchy (no duplicates)
    }


    enum Operator
    {
        Count,
        Intersect,
        Union,
        Filter,
        FilterBy
    }


    enum MeasureOperator
    {
        count,
        countDistinct,
        sum
    }


    public static abstract class MeasureDef
    {
        final MeasureOperator op;
        Member measureMember;
        MeasureDef(Member member, MeasureOperator op)
        {
            this.op = op;
            this.measureMember = member;
        }
    }


    static class CountDistinctMeasureDef extends MeasureDef
    {
        final Level level;

        CountDistinctMeasureDef(Member member, Level l)
        {
            super(member, MeasureOperator.countDistinct);
            level = l;
        }

        @Override
        public String toString()
        {
            return  op.name() + "(" + level.getUniqueName() + ")";
        }
    }


    Result processMembers(QubeQuery.QubeMembersExpr expr) throws SQLException
    {
        if (null != expr.membersSet)
            return new MemberSetResult(expr.membersSet);

        MemberSetResult outer = null;

        if (null != expr.hierarchy && null == expr.level)
        {
            if (expr.childrenMember)
            {
                expr.level = expr.hierarchy.getLevels().get(1);
                expr.hierarchy = null;
            }
        }

        if (null != expr.level)
        {
            outer = new MemberSetResult(expr.level);
        }
        else if (null != expr.hierarchy)
        {
            outer = new MemberSetResult(expr.hierarchy);
        }

        if (null != outer && null != expr.membersQuery)
        {
            Result subquery = processExpr(expr.membersQuery);
            Result filtered;
            filtered = _cubeHelper.membersQuery(outer, (MemberSetResult)subquery);
            return filtered;
        }

        if (null != outer)
            return outer;

        throw new IllegalStateException();
    }


    Result processExpr(QubeQuery.QubeExpr expr) throws SQLException
    {
        while (null != expr.arguments && expr.arguments.size() == 1 && expr.op != QubeQuery.OP.MEMBERS)
            expr = expr.arguments.get(0);

        if (expr.op == QubeQuery.OP.MEMBERS)
        {
            return processMembers((QubeQuery.QubeMembersExpr)expr);
        }

        if (null == expr.arguments || 0 == expr.arguments.size())
            throw new IllegalArgumentException("No arguments provided");

        List<Result> results = new ArrayList<>(expr.arguments.size());
        for (QubeQuery.QubeExpr in : expr.arguments)
        {
            results.add(processExpr(in));
        }

        switch (expr.op)
        {
        case CROSSJOIN:
        case XINTERSECT:
        {
            return crossjoin(expr.op, results);
        }
        case INTERSECT:
        {
            return intersect(expr.op, results);
        }
        case UNION:
        {
            return union(expr.op, results);
        }
        default:
        case MEMBERS:
        {
            throw new IllegalStateException();
        }
        }
    }


    CellSet evaluate(MemberSetResult rowsExpr, MemberSetResult colsExpr, Result filterExpr) throws SQLException
    {
        if (null == rowsExpr && null == colsExpr)
            throw new IllegalArgumentException();

        Level measureLevel = ((CountDistinctMeasureDef)measure).level;

        // STEP 1: create filter set (measure members selected by filter)
        MemberSet filterSet = filter(measureLevel, filterExpr);

        if (_log.isDebugEnabled())
        {
            StringBuilder sb = new StringBuilder();
            sb.append("evaluate()");
            sb.append("\tmeasure   ").append(measure);
            sb.append("\tonrows    ").append(rowsExpr);
            sb.append("\toncolumns ").append(colsExpr);
            sb.append("\twhere     ").append(filterExpr);
            sb.append("\teval      ").append(filterSet);
            _log.debug(sb);
        }

        ArrayList<Number> measureValues = new ArrayList<>();
        int countAllMeasureMembers = measureLevel.getMembers().size();
        int countFilterSet = null == filterSet ? -1 : filterSet.size();
        // if the filter returns all members, it's not much of a filter is it?
        if (countFilterSet == countAllMeasureMembers)
        {
            filterSet = null;
            countFilterSet = -1;
        }

        if (null != colsExpr)
            _cubeHelper.populateCache(measureLevel, colsExpr);
        if (null != rowsExpr)
            _cubeHelper.populateCache(measureLevel, rowsExpr);

        // ONE-AXIS
        if (null == colsExpr || null == rowsExpr)
        {
            Result axis = null==colsExpr ? rowsExpr : colsExpr;
            for (Member m : ((MemberSetResult)axis).getCollection())
            {
                int count;
                if (0 == countFilterSet)
                {
                    count = 0;
                }
                else
                {
                    MemberSet memberSet = _cubeHelper.membersQuery(measureLevel, m);
                    if (null == filterSet)
                        count = memberSet.size();
                    else
                        count = MemberSet.countIntersect(memberSet, filterSet);
                }
                measureValues.add(count);
            }
        }
        // TWO-AXIS
        else
        {
            HashMap<String,MemberSet> quickCache = new HashMap<>();
            for (Member rowMember : rowsExpr.getCollection())
            {
                MemberSet rowMemberSet = null;

                for (Member colMember : colsExpr.getCollection())
                {
                    int count;
                    if (0 == countFilterSet)
                    {
                        count = 0;
                    }
                    else
                    {
                        if (null == rowMemberSet)
                            rowMemberSet = _cubeHelper.membersQuery(measureLevel, rowMember);

                        MemberSet colMemberSet = quickCache.get(colMember.getUniqueName());
                        if (null == colMemberSet)
                        {
                            colMemberSet = _cubeHelper.membersQuery(measureLevel, colMember);
                            quickCache.put(colMember.getUniqueName(),colMemberSet);
                        }
                        if (null == filterSet)
                            count = MemberSet.countIntersect(rowMemberSet, colMemberSet);
                        else
                            count = MemberSet.countIntersect(rowMemberSet, colMemberSet, filterSet);
                    }
                    measureValues.add(count);
                }
            }
        }

        return new _CellSet(
                measureValues,
                colsExpr==null?null:colsExpr.getCollection(),
                rowsExpr==null?null:rowsExpr.getCollection());
    }


    MemberSet filter(Level measureLevel, Result result) throws SQLException
    {
        List<MemberSetResult> list = new ArrayList<>();

        if (result instanceof CrossResult)
        {
            list.addAll(((CrossResult)result).results);
        }
        else if (result instanceof MemberSetResult)
        {
            list.add((MemberSetResult)result);
        }
        else if (null != result)
        {
            throw new IllegalArgumentException();
        }

        if (list.isEmpty())
            return null;   // unfiltered

        MemberSet filteredSet = null;
        for (MemberSetResult memberSetResult : list)
        {
            Level resultLevel = memberSetResult.getLevel();
            Hierarchy resultHierarchy = memberSetResult.getHierarchy();
            String levelUniqueName = (null==resultLevel) ? null : resultLevel.getUniqueName();

            /* NOTE: some CDS queries filter on the subject hierarchy instead of the subject level
             * for backward compatibility, unwind that here
             */
            if (null == resultLevel && null != resultHierarchy && resultHierarchy.getUniqueName().equals(measureLevel.getHierarchy().getUniqueName()))
            {
                // extract only the measure level (e.g. [Subject].[Subject]
                if (memberSetResult.hierarchy != null)
                {
                    memberSetResult = new MemberSetResult(measureLevel);
                }
                else if (null != memberSetResult.members)
                {
                    memberSetResult = new MemberSetResult(memberSetResult.members.onlyFor(measureLevel));
                }
                resultLevel = measureLevel;
                levelUniqueName = resultLevel.getUniqueName();
            }

            MemberSetResult intersectSet;
            if (measureLevel.getUniqueName().equals(levelUniqueName))
            {
                intersectSet = memberSetResult;
            }
            else
            {
                // because we only support COUNT DISTINCT, we can treat this filter like
                // NON EMPTY distinctLevel.members WHERE <filter>,
                // for SUM() or COUNT() this wouldn't work
                intersectSet = _cubeHelper.membersQuery(new MemberSetResult(measureLevel), memberSetResult);
            }
            if (null == filteredSet)
                filteredSet = new MemberSet(intersectSet.getCollection());
            else
                filteredSet.retainAll(intersectSet.getCollection());
        }
        return filteredSet;
    }


    /**
     * We don't actually perform the cross-join, we just collect the results by hierarchy.
     *
     * XINTERSECT is a made-up operator, that is a useful default for filters.  It simply
     * combines sets over the same level by doing an intersection.  NOTE: it could be smarter
     * about combining results within the same _hierarchy_ as well.  However, the main use case is
     * for combinding participant sets.
     */
    Result crossjoin(OP op, Collection<Result> results) throws OlapException
    {
        CrossResult cr = new CrossResult();
        Map<String,MemberSetResult> map = new LinkedHashMap<>();

        for (Result r : results)
        {
            if (!(r instanceof MemberSetResult))
                throw new IllegalStateException();

            Level l = ((MemberSetResult)r).getLevel();

            if (null == l || op == OP.CROSSJOIN)
            {
                cr.results.add((MemberSetResult)r);
            }
            else if (!map.containsKey(l.getUniqueName()))
            {
                map.put(l.getUniqueName(), (MemberSetResult)r);
            }
            else
            {
                Collection<Member> a = map.get(l.getUniqueName()).getCollection();
                Collection<Member> b = ((MemberSetResult) r).getCollection();
                MemberSet set = MemberSet.intersect(a,b);
                map.put(l.getUniqueName(), new MemberSetResult(set));
            }
        }
        cr.results.addAll(map.values());
        return cr;
    }


    Result intersect(OP op, Collection<Result> results) throws OlapException
    {
        if (results.size() == 0)
            throw new IllegalArgumentException();
        if (results.size() == 1)
            return results.iterator().next();

        List<Collection<Member>> sets = new ArrayList<>(results.size());
        for (Result r : results)
        {
            if (!(r instanceof MemberSetResult))
                throw new IllegalArgumentException();
            sets.add(((MemberSetResult)r).getCollection());
        }
        MemberSet s = MemberSet.intersect(sets);
        return new MemberSetResult(s);
    }


    Result union(OP op, Collection<Result> results) throws OlapException
    {
        if (results.size() == 0)
            throw new IllegalArgumentException();
        if (results.size() == 1)
            return results.iterator().next();

        List<Collection<Member>> sets = new ArrayList<>(results.size());
        for (Result r : results)
        {
            if (!(r instanceof MemberSetResult))
                throw new IllegalArgumentException();
            sets.add(((MemberSetResult)r).getCollection());
        }
        MemberSet s = MemberSet.union(sets);
        return new MemberSetResult(s);
    }




    //
    // special optimized subset of query/mdx functionality
    // this method supports:
    //   * simple list/set of members on rows (no hierarchy/cross-products)
    //   * simple list/set of members on columns
    //   * one count(distinct) measure at intersections
    //

    public CellSet executeQuery() throws BindException,SQLException
    {
        // TODO : should specify in JSON, shouldn't need to infer here
        if (null == qq.countDistinctMember)
        {
            for (Measure m : cube.getMeasures())
            {
                if (m.getName().endsWith("Count") && m.getName().equals("RowCount"))
                    qq.countDistinctMember = m;
            }
        }

        if (null != qq.countDistinctMember && null == qq.countDistinctLevel)
        {
            String name = qq.countDistinctMember.getName();
            if (name.endsWith("Count"))
                name = name.substring(0,name.length()-5);
            Hierarchy h = cube.getHierarchies().get(name);
            if (null == h)
                h = cube.getHierarchies().get("Participant");
            if (null == h)
                h = cube.getHierarchies().get("Subject");
            if (null == h)
                h = cube.getHierarchies().get("Patient");
            if (h != null)
                qq.countDistinctLevel = h.getLevels().get(h.getLevels().size()-1);
        }

        if (null == qq.countDistinctLevel)
            throw new IllegalArgumentException("No count distinct measure definition found");

        this.measure = new CountDistinctMeasureDef(qq.countDistinctMember, qq.countDistinctLevel);
        Result filterExpr=null, rowsExpr=null, columnsExpr=null;

        if (null != qq.onColumns)
            columnsExpr = processExpr(qq.onColumns);
        if (null != qq.onRows)
            rowsExpr = processExpr(qq.onRows);
        if (null != qq.filters && qq.filters.arguments.size() > 0)
            filterExpr = processExpr(qq.filters);

        CellSet ret;
        ret = evaluate((MemberSetResult)rowsExpr, (MemberSetResult)columnsExpr, filterExpr);
        return ret;
    }


    static StringKeyCache<MemberSet> _resultsCache = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, TimeUnit.HOURS.toMillis(1), "olap - count distinct queries");

    MemberSet resultsCacheGet(String query)
    {
        String key = cachePrefix + query;
        MemberSet m = _resultsCache.get(key);
        if (null == m)
            return null;
        return m.attach(levelMap);
    }

    void resultsCachePut(String query, MemberSet m)
    {
        String key = cachePrefix + query;
        _resultsCache.put(key, m.detach());
    }



    OlapConnection getOlapConnection()
    {
        return connection;
    }


    // return sets/counts from the cube,
    class CubeDataSourceHelper
    {
        CellSet execute(String query)
        {
            OlapStatement stmt = null;
            CellSet cs = null;
            try
            {
                OlapConnection conn = getOlapConnection();
                stmt = conn.createStatement();
                QueryProfiler.getInstance().ensureListenerEnvironment();
                _log.debug("\nSTART executeOlapQuery: --------------------------    --------------------------    --------------------------\n" + query);
                long ms = System.currentTimeMillis();
                cs = stmt.executeOlapQuery(query);
                long d = System.currentTimeMillis() - ms;
                QueryProfiler.getInstance().track(null, "-- MDX\n" + query, null, d, null, true);
                _log.debug("\nEND executeOlapQuery: " + DateUtil.formatDuration(d) + " --------------------------    --------------------------    --------------------------\n");
                return cs;
            }
            catch (RuntimeException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
            finally
            {
                ResultSetUtil.close(cs);
                ResultSetUtil.close(stmt);
            }
        }


        String queryCrossjoin(MemberSetResult from, MemberSetResult to)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    "SELECT\n" +
                            "  [Measures].DefaultMember ON COLUMNS,\n" +
                            "  NON EMPTY CROSSJOIN(");
            from.toMdxSet(sb);
            sb.append(",");
            to.toMdxSet(sb);
            sb.append(") ON ROWS\n");
//            sb.append(from.getUniqueName()).append(".members,");
//            sb.append(to.getUniqueName()).append(".members ON ROWS\n");
            sb.append("FROM ").append(cube.getUniqueName());
            return sb.toString();
        }


        String queryCrossjoin(Level from, MemberSetResult to)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    "SELECT\n" +
                            "  [Measures].DefaultMember ON COLUMNS,\n" +
                            "  NON EMPTY CROSSJOIN(");
            sb.append(from.getUniqueName()).append(".members");
            sb.append(",");
            to.toMdxSet(sb);
            sb.append(") ON ROWS\n");
//            sb.append(from.getUniqueName()).append(".members,");
//            sb.append(to.getUniqueName()).append(".members ON ROWS\n");
            sb.append("FROM ").append(cube.getUniqueName());
            return sb.toString();
        }


        String queryIsNotEmpty(Level from, Member m)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(
                    "SELECT\n" +
                            "  [Measures].DefaultMember ON COLUMNS,\n" +
                            "  NON EMPTY CROSSJOIN(");
            sb.append(from.getUniqueName()).append(".members");
            sb.append(",");
            sb.append(m.getUniqueName());
            sb.append(") ON ROWS\n");
            sb.append("FROM ").append(cube.getUniqueName());
            return sb.toString();
        }


        /**
         * return a set of members in <MemberSetResult=outer> that intersect (non empty cross join) with <MemberSetResult=sub>.
         * Note that this basically gives OR or UNION semantics w/respect to the sub members.
         */
        MemberSetResult membersQuery(MemberSetResult outer, MemberSetResult sub) throws SQLException
        {
            MemberSet set;

            if (null != outer.level && null != sub.members && sub.members.size() == 1)
            {
                Iterator<Member> it = sub.members.iterator();
                it.hasNext();
                set = membersQuery(outer.level, it.next());
            }
            else
            {
                String query = queryCrossjoin(outer,sub);

                set = resultsCacheGet(query);
                if (null == set)
                {
                    try (CellSet cs = execute(query))
                    {
                        set = new MemberSet();
                        for (Position p :  cs.getAxes().get(1).getPositions())
                        {
                            Member m = p.getMembers().get(0);
                            set.add(m);
                        }
                    }
                    resultsCachePut(query, set);
                }
            }

            return new MemberSetResult(set);
        }


        /**
         * return a set of members in <Level=outer> that intersect (non empty cross join) with <Member=sub>
         */
        MemberSet membersQuery(Level outer, Member sub) throws SQLException
        {
            if (sub.getHierarchy().getUniqueName().equals(outer.getHierarchy().getUniqueName()))
            {
                MemberSet s = new MemberSet();
                addMemberAndChildrenInLevel(sub, outer, s);
                return s;
            }

            String query = queryIsNotEmpty(outer, sub);
            MemberSet s = resultsCacheGet(query);
            if (null != s)
                return s;

            try (CellSet cs = execute(query))
            {
                MemberSet set = new MemberSet();
                for (Position p :  cs.getAxes().get(1).getPositions())
                {
                    Member m = p.getMembers().get(0);
//                  CONSIDER: verify that count is not 0?  if (0 != cs.getCell(p).getValue())
                    set.add(m);
                }
                resultsCachePut(query, set);
                return set;
            }
        }

        void populateCache(Level outerLevel, MemberSetResult inner) throws SQLException
        {
            boolean cacheMiss = false;

            // We cache by individual members, but we don't necessaryily want to load the cache one set at a time
            // we could find just the members that are not yet cached, but I'm just going to check if ANY are missing
            Hierarchy h = null;
            for (Member sub : inner.getCollection())
            {
                if (sub.getHierarchy().getUniqueName().equals(outerLevel.getHierarchy().getUniqueName()))
                    continue;
                String query = queryIsNotEmpty(outerLevel, sub);
                MemberSet s = resultsCacheGet(query);
                if (null == s)
                {
                    cacheMiss = true;
                    break;
                }
            }
            if (!cacheMiss)
                return;

            HashMap<String,MemberSet> sets = new HashMap<>();
            for (Member sub : inner.getCollection())
                sets.put(sub.getUniqueName(), new MemberSet());

            String queryXjoin = queryCrossjoin(outerLevel, inner);
            try (CellSet cs = execute(queryXjoin))
            {
                for (Position p :  cs.getAxes().get(1).getPositions())
                {
                    Member outerMember = p.getMembers().get(0);
                    Member sub = p.getMembers().get(1);
                    assert outerMember.getLevel().getUniqueName().equals(outerLevel.getUniqueName());
                    MemberSet s = sets.get(sub.getUniqueName());
                    if (null == s)
                        _log.warn("Unexpected member in cellset result: " + sub.getUniqueName());
                    else
                        s.add(outerMember);
                }
            }
            for (Member sub : inner.getCollection())
            {
                String query = queryIsNotEmpty(outerLevel, sub);
                MemberSet s = sets.get(sub.getUniqueName());
                if (null != s)
                    resultsCachePut(query,s);
            }
        }
    }


    CubeDataSourceHelper _cubeHelper = new CubeDataSourceHelper();


    // TODO SqlHelper requires some sort of mapping to the sql schema
    class SqlDataSourceHelper
    {

    }


    class _CellSet extends QubeCellSet
    {
        _CellSet(List<Number> results, Collection<Member> rows, Collection<Member> cols)
        {
            super(BitSetQueryImpl.this.cube, BitSetQueryImpl.this.measure, results, rows, cols);
        }
    }



    static public void invalidateCache(Container c)
    {
        _resultsCache.removeUsingPrefix( "" + c.getRowId() + "/");
    }
    static public void invalidateCache(OlapSchemaDescriptor sd)
    {
        _resultsCache.clear();
    }
    static public void invalidateCache()
    {
        _resultsCache.clear();
    }
}
