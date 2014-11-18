/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.di.steps;

import org.apache.xmlbeans.XmlException;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.etl.xml.TransformType;

/**
 * User: matthewb
 * Date: 2013-04-03
 * Time: 2:22 PM
 *
 * Metadata for a simple query transform
 */
public class SimpleQueryTransformStepMeta extends StepMetaImpl
{
    @Override
    public String toString()
    {
        return getSourceSchema().toString() + "." + getSourceQuery() + " --> " +
                getFullTargetString();
    }

    @Override
    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        if (null != transformXML.getSource())
        {
            parseSource(transformXML);
        }
        else throw new XmlException(TransformManager.INVALID_SOURCE);

        if (null != transformXML.getDestination())
        {
            parseDestination(transformXML);
        }
        else throw new XmlException(TransformManager.INVALID_DESTINATION);
    }
}
