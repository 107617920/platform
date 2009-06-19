/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.query.snapshot;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.*;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
 * User: Karl Lum
 * Date: Jul 8, 2008
 * Time: 2:23:17 PM
 */

public abstract class AbstractSnapshotProvider implements QuerySnapshotService.I
{
    private static final Map<String, Class> propertyClassMap = new HashMap<String, Class>();

    static {

        propertyClassMap.put(boolean.class.getName(), Boolean.class);
        propertyClassMap.put(int.class.getName(), Integer.class);
        propertyClassMap.put(short.class.getName(), Integer.class);
        propertyClassMap.put(long.class.getName(), Double.class);
        propertyClassMap.put(double.class.getName(), Double.class);
        propertyClassMap.put(float.class.getName(), Float.class);
    }

    public String getDescription()
    {
        return null;
    }

    public ActionURL createSnapshot(QuerySnapshotForm form, List<String> errors) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public ActionURL updateSnapshot(QuerySnapshotForm form, List<String> errors) throws Exception
    {
        throw new UnsupportedOperationException();
    }

    public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def, List<String> errors) throws Exception
    {
        def.save(context.getUser(), context.getContainer());
        return null;
    }

    public List<DisplayColumn> getDisplayColumns(QueryForm queryForm) throws Exception
    {
        QueryView view = QueryView.create(queryForm);
        return view.getDisplayColumns();
    }

    public ActionURL getCreateWizardURL(QuerySettings settings, ViewContext context)
    {
        QuerySettings qs = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);
        return PageFlowUtil.urlProvider(QueryUrls.class).urlCreateSnapshot(context.getContainer()).
                addParameter(qs.param(QueryParam.schemaName), settings.getSchemaName()).
                addParameter(qs.param(QueryParam.queryName), settings.getQueryName());
    }

    public ActionURL getEditSnapshotURL(QuerySettings settings, ViewContext context)
    {
        return PageFlowUtil.urlProvider(QueryUrls.class).urlCustomizeSnapshot(context.getContainer());  
    }

    /**
     * Creates and initializes a snapshot definition from the snapshot form. The definition will still need
     * to be saved to persist to the db.
     */
    protected QuerySnapshotDefinition createSnapshotDef(QuerySnapshotForm form)
    {
        QueryDefinition queryDef = form.getQueryDef();
        if (queryDef != null)
        {
            QuerySnapshotDefinition snapshot = QueryService.get().createQuerySnapshotDef(queryDef,  form.getSnapshotName());

            snapshot.setColumns(form.getFieldKeyColumns());
            snapshot.setUpdateDelay(form.getUpdateDelay());

            if (form.getViewName() != null)
            {
                ViewContext context = form.getViewContext();

                CustomView customSrc = queryDef.getCustomView(context.getUser(), context.getRequest(), form.getViewName());
                if (customSrc != null)
                {
                    snapshot.setColumns(customSrc.getColumns());
                    if (customSrc.hasFilterOrSort())
                        snapshot.setFilter(customSrc.getFilterAndSort());
                }
            }
            return snapshot;
        }
        return null;
    }

    public static DomainProperty addAsDomainProperty(Domain domain, ColumnInfo column)
    {
        PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(column.getPropertyURI(), domain.getContainer());

        DomainProperty prop = domain.addProperty();
        prop.setLabel(column.getCaption());
        prop.setName(column.getName());

        Class clz = column.getJavaClass();
        // need to map primitives to object class equivalents
        if (propertyClassMap.containsKey(clz.getName()))
            clz = propertyClassMap.get(clz.getName());

        PropertyType type = PropertyType.getFromClass(clz);
        prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
        prop.setDescription(column.getDescription());
        prop.setFormat(column.getFormatString());
        prop.setPropertyURI(getPropertyURI(domain, column));

        if (pd != null && pd.getLookupQuery() != null)
        {
            String container = pd.getLookupContainer();
            Container c = null;
            if (container != null)
            {
                if (GUID.isGUID(container))
                    c = ContainerManager.getForId(container);
                if (c == null)
                    c = ContainerManager.getForPath(container);
            }
            Lookup lu = new Lookup(c, pd.getLookupSchema(), pd.getLookupQuery());
            prop.setLookup(lu);
            prop.setRequired(pd.isRequired());
        }
        return prop;
    }

    public static String getPropertyURI(Domain domain, ColumnInfo column)
    {
        return domain.getTypeURI() + "." + column.getName();
    }
}