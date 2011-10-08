/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jul 13, 2007
 */
public class AssayService
{
    static private Interface INSTANCE;

    public static final String BATCH_COLUMN_NAME = "Batch";

    static public synchronized Interface get()
    {
        return INSTANCE;
    }

    static public synchronized void setInstance(Interface impl)
    {
        INSTANCE = impl;
    }

    public interface Interface
    {
        public void registerAssayProvider(AssayProvider provider);
        public AssayProvider getProvider(String providerName);
        public AssayProvider getProvider(ExpProtocol protocol);
        public AssayProvider getProvider(ExpRun run);
        public List<AssayProvider> getAssayProviders();
        public WebPartView createAssayListView(ViewContext context, boolean portalView);

        ModelAndView createAssayDesignerView(Map<String, String> properties);
        ModelAndView createAssayImportView(Map<String, String> properties);
        ModelAndView createListChooserView(Map<String, String> properties);

        ExpRunTable createRunTable(ExpProtocol protocol, AssayProvider provider, User user, Container container);

        public AssaySchema createSchema(User user, Container container);

        public String getBatchesTableName(ExpProtocol protocol);
        public String getRunsTableName(ExpProtocol protocol);
        public String getResultsTableName(ExpProtocol protocol);

        List<ExpProtocol> getAssayProtocols(Container container);

        /**
         * Populates the import button with possible containers
         * @param isStudyView true if this view is from a study, and thus should exclude the current container
         * unless it already has assay data in it
         */
        public List<ActionButton> getImportButtons(ExpProtocol protocol, User user, Container currentContainer, boolean isStudyView);

        /**
         * Creates a batch object but does not save it to the database
         * @param container location for this batch to live
         * @param name preferred name for the batch. If null, a default name will be assigned. If the name is already in
         */
        public ExpExperiment createStandardBatch(Container container, String name, ExpProtocol protocol);

        /** Ensures that the batch name is unique within the container. Will add unique numeric suffix until it is. */
        public ExpExperiment ensureUniqueBatchName(ExpExperiment batch, ExpProtocol protocol, User user);

        /**
         * @return the batch object for the assay run, if it has one.
         */
        @Nullable
        public ExpExperiment findBatch(ExpRun run);

        public void indexAssays(SearchService.IndexTask task, Container c);

        void upgradeAssayDefinitions(User upgradeUser, double targetVersion);
    }
}
