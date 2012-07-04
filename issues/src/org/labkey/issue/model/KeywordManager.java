package org.labkey.issue.model;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.util.HString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.issue.ColumnType;

import java.sql.SQLException;
import java.util.Collection;

/**
 * User: adam
 * Date: 6/25/12
 * Time: 7:49 PM
 */
public class KeywordManager
{
    private static final Logger LOG = Logger.getLogger(KeywordManager.class);

    public static final Object KEYWORD_LOCK = new Object();
    public static final Cache<String, Collection<Keyword>> KEYWORD_CACHE = CacheManager.getCache(1000, CacheManager.HOUR, "Issue Keywords");

    private static String getCacheKey(Container c, ColumnType type)
    {
        return c.getId() + "/" + type.getOrdinal();
    }

    public static Collection<Keyword> getKeywords(final Container c, final ColumnType type)
    {
        return KEYWORD_CACHE.get(getCacheKey(c, type), c, new CacheLoader<String, Collection<Keyword>>() {
            @Override
            public Collection<Keyword> load(String key, @Nullable Object argument)
            {
                assert type.getOrdinal() > 0;   // Ordinal 0 ==> no pick list (e.g., custom integer columns)

                SimpleFilter filter = new SimpleFilter("Container", c.getId()).addCondition("Type", type.getOrdinal());
                Sort sort = new Sort("Keyword");

                Selector selector = new TableSelector(IssuesSchema.getInstance().getTableInfoIssueKeywords(), PageFlowUtil.set("Keyword", "Default", "Container", "Type"), filter, sort);
                Collection<Keyword> keywords = selector.getCollection(Keyword.class);

                if (keywords.isEmpty())
                {
                    HString[] initialValues = type.getInitialValues();

                    if (initialValues.length > 0)
                    {
                        // First reference in this container... save away initial values & default
                        addKeyword(c, type, initialValues);
                        setKeywordDefault(c, type, type.getInitialDefaultValue());
                    }

                    keywords = selector.getCollection(Keyword.class);
                }

                return keywords;
            }
        });
    }


    public static void addKeyword(Container c, ColumnType type, HString... keywords)
    {
        synchronized (KEYWORD_LOCK)
        {
            for (HString keyword : keywords)
            {
                SqlExecutor executor = new SqlExecutor(IssuesSchema.getInstance().getSchema(), new SQLFragment(
                        "INSERT INTO " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " (Container, Type, Keyword) VALUES (?, ?, ?)",
                        c.getId(), type.getOrdinal(), keyword));
                executor.execute();
            }

            KEYWORD_CACHE.remove(getCacheKey(c, type));
        }
    }


    // Clear old default value and set new one
    public static void setKeywordDefault(Container c, ColumnType type, HString keyword)
    {
        clearKeywordDefault(c, type);

        String selectName = IssuesSchema.getInstance().getTableInfoIssueKeywords().getColumn("Default").getSelectName();

        new SqlExecutor(IssuesSchema.getInstance().getSchema(), new SQLFragment(
                "UPDATE " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " SET " + selectName + "=? WHERE Container = ? AND Type = ? AND Keyword = ?",
                Boolean.TRUE, c.getId(), type.getOrdinal(), keyword)
            ).execute();

        KEYWORD_CACHE.remove(getCacheKey(c, type));
    }


    // Clear existing default value
    public static void clearKeywordDefault(Container c, ColumnType type)
    {
        String selectName = IssuesSchema.getInstance().getTableInfoIssueKeywords().getColumn("Default").getSelectName();

        new SqlExecutor(IssuesSchema.getInstance().getSchema(), new SQLFragment(
                "UPDATE " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " SET " + selectName + " = ? WHERE Container = ? AND Type = ?",
                Boolean.FALSE, c.getId(), type.getOrdinal())
            ).execute();

        KEYWORD_CACHE.remove(getCacheKey(c, type));
    }


    public static void deleteKeyword(Container c, ColumnType type, HString keyword)
    {
        Collection<Keyword> keywords = null;

        synchronized (KEYWORD_LOCK)
        {
            try
            {
                Table.execute(IssuesSchema.getInstance().getSchema(),
                        "DELETE FROM " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " WHERE Container = ? AND Type = ? AND Keyword = ?",
                        c.getId(), type.getOrdinal(), keyword);
                KEYWORD_CACHE.remove(getCacheKey(c, type));
                keywords = getKeywords(c, type);
            }
            catch (SQLException x)
            {
                LOG.error("deleteKeyword", x);
            }
        }

        //Check to see if the last keyword of a required field was deleted, if so no longer make the field required.
        if (keywords == null || keywords.isEmpty())
        {
            String columnName = type.getColumnName();
            HString requiredFields = IssueManager.getRequiredIssueFields(c);

            if (null != columnName && requiredFields.contains(columnName))
            {
                //Here we want to remove the type from the required fields.
                requiredFields = requiredFields.replace(columnName, "");
                if (requiredFields.length() > 0)
                {
                    if (requiredFields.charAt(0) == ';')
                    {
                       requiredFields = requiredFields.substring(1);
                    }
                    else if (requiredFields.charAt(requiredFields.length()-1) == ';')
                    {
                       requiredFields = requiredFields.substring(0, requiredFields.length()-1);
                    }
                    else
                    {
                       requiredFields = requiredFields.replace(";;", ";");
                    }
                }

                try
                {
                    IssueManager.setRequiredIssueFields(c, requiredFields.toString());
                }
                catch (SQLException x)
                {
                    LOG.error("deleteKeyword", x);
                }
            }
        }
    }


    public static HString getKeywordOptions(final Container c, final ColumnType type)
    {
        Collection<Keyword> keywords = getKeywords(c, type);
        StringBuilder sb = new StringBuilder(keywords.size() * 30);

        if (type.allowBlank())
            sb.append("<option></option>\n");

        for (Keyword keyword : keywords)
        {
            sb.append("<option>");
            sb.append(PageFlowUtil.filter(keyword.getKeyword()));
            sb.append("</option>\n");
        }

        return new HString(sb);
    }


    public static class Keyword
    {
        private HString _keyword;
        private boolean _default = false;

        public boolean isDefault()
        {
            return _default;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDefault(boolean def)
        {
            _default = def;
        }

        public HString getKeyword()
        {
            return _keyword;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setKeyword(HString keyword)
        {
            _keyword = keyword;
        }
    }
}
