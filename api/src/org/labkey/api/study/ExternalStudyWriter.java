/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.api.study;

import org.labkey.api.admin.ImportContext;
import org.labkey.api.writer.Writer;
import org.labkey.study.xml.StudyDocument;

/*
* User: adam
* Date: Aug 26, 2009
* Time: 1:26:41 PM
*/
public interface ExternalStudyWriter extends Writer<Study, ImportContext<StudyDocument.Study>>
{
}
