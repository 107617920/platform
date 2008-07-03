/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * <code>WorkDirectoryLocal</code>
 *
 * @author brendanx
 */
public class WorkDirectoryLocal extends AbstractWorkDirectory
{
    public static class Factory implements WorkDirFactory
    {
        public WorkDirectory createWorkDirectory(String jobId, FileAnalysisJobSupport support, Logger log) throws IOException
        {
            File dir = FT_WORK_DIR.newFile(support.getAnalysisDirectory(),
                    support.getBaseName());

            return new WorkDirectoryLocal(support, dir, log);
        }
    }

    public WorkDirectoryLocal(FileAnalysisJobSupport support, File dir, Logger log) throws IOException
    {
        super(support, dir, log);
    }

    public File inputFile(File fileInput, boolean forceCopy) throws IOException
    {
        if (!forceCopy)
            return fileInput;
        return copyInputFile(fileInput);
    }

    protected CopyingResource acquireCopyingLock()
    {
        return new CopyingResource();
    }
}
