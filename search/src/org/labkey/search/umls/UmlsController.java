/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.search.umls;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.JspView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Mar 4, 2010
 * Time: 1:28:53 PM
 */
public class UmlsController extends SpringActionController
{
    private static final SpringActionController.DefaultActionResolver _actionResolver = new SpringActionController.DefaultActionResolver(UmlsController.class);

    public UmlsController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    @RequiresSiteAdmin
    public class DebugAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            RRF_Reader l = new RRF_Reader(new File("/Users/Matthew/Desktop/META"));       // "/Volumes/STORAGE/UMLS/2009AB/META"));
            Iterator<SemanticType> types = l.getTypes(null);
            TreeMap<String, String> map = new TreeMap<String, String>();
            while (types.hasNext())
            {
                SemanticType t = types.next();
                map.put(t.STN, t.STY);
            }

            Writer out = getViewContext().getResponse().getWriter();
            out.write("<pre>\n");
            for (Map.Entry<String, String> e : map.entrySet())
            {
                out.write(e.getKey());
                out.write("\t");
                out.write(e.getValue());
                out.write("\n");
            }
            out.write("</pre>\n");
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    public static class ConceptForm
    {
        String CUI;
        public void setCUI(String CUI)
        {
            this.CUI=CUI;
        }
        public void setCui(String CUI)
        {
            this.CUI=CUI;
        }
        public String getCUI()
        {
            return CUI;
        }
    }


    @RequiresNoPermission
    public class ConceptAction extends SimpleViewAction<ConceptForm>
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }

        @Override
        public ModelAndView getView(ConceptForm conceptForm, BindException errors) throws Exception
        {
            return new JspView<ConceptForm>(UmlsController.class, "concept.jsp", conceptForm, errors);
        }
    }
    


    public static final SearchService.SearchCategory umlsCategory = new SearchService.SearchCategory("umls", "UMLS Concepts");

    public static final Object jobLock = new Object();
    public static RRF_Loader job = null;

    
    @RequiresSiteAdmin
    public class IndexAction extends FormViewAction
    {
        public PollingUtil.PollKey _key = null;
        
        public void validateCommand(Object target, Errors errors)
        {
        }

        public ModelAndView getView(Object o, boolean reshow, BindException errors) throws Exception
        {
            if (_key == null)
            {
                synchronized (jobLock)
                {
                    if (null == job || job.isDone())
                        job = null;
                    if (null != job)
                        _key = job.getPollKey();
                }
            }
            return new JspView<IndexAction>(UmlsController.class,"index.jsp",this,errors);
        }

        public URLHelper getSuccessURL(Object o)
        {
            return new ActionURL(IndexAction.class, getViewContext().getContainer());
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            synchronized (jobLock)
            {
                if (null != job && !job.isDone())
                    return true;
                job = new RRF_Loader(new File("/Volumes/STORAGE/UMLS/2009AB/META"));
                JobRunner.getDefault().submit(job);
            }
            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    private static String h(String s)
    {
        return PageFlowUtil.filter(s);
    }


    static private String format(Object o)
    {
        if (o instanceof Definition)
        {
            Definition d = (Definition) o;
            if ("Y".equals(d.SUPPRESS))
                return "<strike>" + h(d.toString()) + "</strike>";
            return h(d.toString());
        }

        if (o instanceof ConceptName)
        {
            ConceptName n = (ConceptName) o;
            if ("Y".equals(n.SUPPRESS))
                return "<strike>" + h(n.toString()) + "</strike>";
            if ("Y".equals(n.ISPREF))
                return "<b>" + h(n.toString()) + "</b>";
            return h(n.toString());
        }

        if (o instanceof SemanticType)
        {
            SemanticType s = (SemanticType) o;
            return h(s.toString());
        }

        return h(o.toString());
    }



    public static ConceptName[] getNames(DbSchema umls, String CUI)
    {
        try
        {
            return Table.executeQuery(umls, "SELECT * FROM umls.MRCONSO WHERE CUI=? AND LAT='ENG'", new Object[]{CUI}, ConceptName.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static Related[] getRelated(DbSchema umls, String CUI)
    {
        try
        {
            return Table.executeQuery(umls, "SELECT * FROM umls.MRREL WHERE CUI1=? UNION SELECT * FROM umls.MRREL WHERE CUI2=?", new Object[]{CUI, CUI}, Related.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static Definition[] getDefinitions(DbSchema umls, String CUI)
    {
        try
        {
            return Table.executeQuery(umls, "SELECT * FROM umls.MRDEF WHERE CUI=?", new Object[]{CUI}, Definition.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static SemanticType[] getSemanticType(DbSchema umls, String CUI)
    {
        try
        {
            return Table.executeQuery(umls, "SELECT * FROM umls.MRSTY WHERE CUI=?", new Object[]{CUI}, SemanticType.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
    }

    public static Map<String,String> getNames(DbSchema umls, Set<String> cuis)
    {
        if (null == cuis || cuis.isEmpty())
            return Collections.emptyMap();
        ResultSet rs = null;
        try
        {
            SQLFragment sqlf = new SQLFragment("SELECT CUI, MIN(STR) FROM umls.MRCONSO WHERE LAT='ENG' AND CUI IN(");
            String comma="";
            for (String cui:cuis)
            {
                sqlf.append(comma).append("?");
                sqlf.add(cui);
                comma=",";
            }
            sqlf.append(") GROUP BY CUI");
            rs = Table.executeQuery(umls, sqlf);
            Map<String,String> map = new HashMap<String,String>();
            while (rs.next())
                map.put(rs.getString(1), rs.getString(2));
            return map;
        }
        catch (SQLException x)
        {
            throw new RuntimeException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    
    DbSchema _schema;

    DbSchema getSchema()
    {
        if (null == _schema)
            _schema = DbSchema.get("umls");
        return _schema;
    }
}