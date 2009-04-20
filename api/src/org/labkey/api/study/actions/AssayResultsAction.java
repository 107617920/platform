/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ActionNames;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.AssayResultsView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:30:05 PM
*/
@RequiresPermission(ACL.PERM_READ)
@ActionNames( "assayResults, assayData" )
public class AssayResultsAction extends BaseAssayAction<ProtocolIdForm>
{
    private ExpProtocol _protocol;

    public ModelAndView getView(ProtocolIdForm form, BindException errors) throws Exception
    {
        ViewContext context = getViewContext();
        _protocol = getProtocol(form);
        AssayProvider provider = AssayService.get().getProvider(_protocol);

        ModelAndView resultsView = provider.createResultsView(context, _protocol);
        if (resultsView != null)
            return resultsView;
        return new AssayResultsView(_protocol, false);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName() + " Batches", PageFlowUtil.urlProvider(AssayUrls.class).getAssayBatchesURL(getContainer(), _protocol, null));
        result.addChild(_protocol.getName() + " Runs", PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(getContainer(), _protocol));
        result.addChild(_protocol.getName() + " Results");
        return result;
    }

    public AppBar getAppBar()
    {
        return getAppBar(_protocol);
    }
}
