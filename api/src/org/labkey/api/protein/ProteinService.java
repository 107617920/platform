/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.protein;

import org.labkey.api.action.QueryViewAction;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.springframework.validation.BindException;

import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: May 3, 2012
 */
public interface ProteinService
{
    public int ensureProtein(String sequence, String organism, String name, String description);

    public int ensureProteinAndIdentifier(String sequence, String organism, String identifer, String description, String identifierType);

    public void ensureIdentifiers(int seqId, Map<String, String> identifierAndTypes);

    public void registerProteinSearchView(QueryViewProvider<ProteinSearchForm> provider);
    public void registerPeptideSearchView(QueryViewProvider<PeptideSearchForm> provider);

    public List<QueryViewProvider<PeptideSearchForm>> getPeptideSearchViews();

    /** @param aaRowWidth the number of amino acids to display in a single row */
    public WebPartView getProteinCoverageView(int seqId, String[] peptides, int aaRowWidth, boolean showEntireFragmentInCoverage);

    /** @return a web part with all of the annotations and identifiers we know for a given protein */
    public WebPartView getAnnotationsView(int seqId);

    public String getProteinSequence(int seqId);

    public interface QueryViewProvider<FormType>
    {
        public String getDataRegionName();
        public QueryView createView(ViewContext viewContext, FormType form, BindException errors);
    }

    public static abstract class PeptideSearchForm extends QueryViewAction.QueryExportForm
    {
        public enum ParamNames
        {
            pepSeq,
            exact,
            subfolders,
            runIds
        }

        private String _pepSeq = "";
        private boolean _exact = false;
        private boolean _subfolders = false;
        private String _runIds = null;

        public String getPepSeq()
        {
            return _pepSeq;
        }

        public void setPepSeq(String pepSeq)
        {
            _pepSeq = pepSeq;
        }

        public boolean isExact()
        {
            return _exact;
        }

        public void setExact(boolean exact)
        {
            _exact = exact;
        }

        public boolean isSubfolders()
        {
            return _subfolders;
        }

        public void setSubfolders(boolean subfolders)
        {
            _subfolders = subfolders;
        }

        public String getRunIds()
        {
            return _runIds;
        }

        public void setRunIds(String runIds)
        {
            _runIds = runIds;
        }

        public abstract SimpleFilter.FilterClause createFilter(String sequenceColumnName);
    }

    public abstract static class ProteinSearchForm extends QueryViewAction.QueryExportForm
    {
        private String _identifier;
        private String _peptideFilterType = "none";
        private Float _peptideProphetProbability;
        private boolean _includeSubfolders;
        private boolean _exactMatch;
        private boolean _restrictProteins;
        protected String _defaultCustomView;

        private int[] _seqIds;

        public String getDefaultCustomView()
        {
            return _defaultCustomView;
        }

        public void setDefaultCustomView(String defaultCustomView)
        {
            _defaultCustomView = defaultCustomView;
        }

        public boolean isExactMatch()
        {
            return _exactMatch;
        }

        public void setExactMatch(boolean exactMatch)
        {
            _exactMatch = exactMatch;
        }

        public String getPeptideFilterType()
        {
            return _peptideFilterType;
        }

        public void setPeptideFilterType(String peptideFilterType)
        {
            _peptideFilterType = peptideFilterType;
        }

        public Float getPeptideProphetProbability()
        {
            return _peptideProphetProbability;
        }

        public void setPeptideProphetProbability(Float peptideProphetProbability)
        {
            _peptideProphetProbability = peptideProphetProbability;
        }

        public String getIdentifier()
        {
            return _identifier;
        }

        public void setIdentifier(String identifier)
        {
            _identifier = identifier;
        }

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }

        public boolean isRestrictProteins()
        {
            return _restrictProteins;
        }

        public void setRestrictProteins(boolean restrictProteins)
        {
            _restrictProteins = restrictProteins;
        }

        public abstract int[] getSeqId();
    }
}
