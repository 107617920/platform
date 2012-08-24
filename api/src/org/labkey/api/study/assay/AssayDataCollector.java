/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.view.HttpView;

import java.util.Map;
import java.io.File;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public interface AssayDataCollector<ContextType extends AssayRunUploadContext>
{
    public static final String PRIMARY_FILE = "__primaryFile__";

    public enum AdditionalUploadType
    {
        Disallowed(null), AlreadyUploaded("Save and Import Next File"), UploadRequired("Save and Import Another Run");

        private String _buttonText;

        private AdditionalUploadType(String buttonText)
        {
            _buttonText = buttonText;
        }

        public String getButtonText()
        {
            return _buttonText;
        }
    }

    public HttpView getView(ContextType context) throws ExperimentException;

    public String getShortName();

    public String getDescription(ContextType context);

    /** Map of original file name to file on disk */
    @NotNull
    public Map<String, File> createData(ContextType context) throws IOException, ExperimentException;

    public boolean isVisible();

    Map<String, File> uploadComplete(ContextType context, @Nullable ExpRun run) throws ExperimentException;

    public AdditionalUploadType getAdditionalUploadType(ContextType context);
}
