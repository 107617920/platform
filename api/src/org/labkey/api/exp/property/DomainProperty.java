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

package org.labkey.api.exp.property;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;

import java.util.List;
import java.util.Set;

public interface DomainProperty extends ImportAliasable
{
    int getPropertyId();
    Container getContainer();
    String getRangeURI();
    String getDescription();
    String getFormat();
    String getConceptURI();
    
    Domain getDomain();
    IPropertyType getType();
    FacetingBehaviorType getFacetingBehavior();
    boolean isRequired();
    boolean isHidden();
    boolean isShownInInsertView();
    boolean isShownInUpdateView();
    boolean isShownInDetailsView();
    boolean isMeasure();
    boolean isDimension();

    void delete();

    void setName(String name);
    void setDescription(String description);
    void setLabel(String caption);
    void setConceptURI(String conceptURI);
    void setType(IPropertyType type);
    void setFacetingBehavior(FacetingBehaviorType type);
    void setPropertyURI(String uri);
    void setRangeURI(String uri);
    void setFormat(String s);
    void setRequired(boolean b);
    void setHidden(boolean hidden);
    void setShownInInsertView(boolean shown);
    void setShownInUpdateView(boolean shown);
    void setShownInDetailsView(boolean shown);
    void setMvEnabled(boolean mv);
    void setMeasure(boolean isMeasure);
    void setDimension(boolean isDimension);

    void setImportAliasSet(Set<String> aliases);
    void setURL(String url);
    String getURL();

    DefaultValueType getDefaultValueTypeEnum();

    void setDefaultValueTypeEnum(DefaultValueType defaultValueType);

    int getSqlType();
    int getScale();
    String getInputType();

    Lookup getLookup();

    void setLookup(Lookup lookup);

    PropertyDescriptor getPropertyDescriptor();

    List<ConditionalFormat> getConditionalFormats();
    void setConditionalFormats(List<ConditionalFormat> formats);

    @NotNull
    List<? extends IPropertyValidator> getValidators();
    void addValidator(IPropertyValidator validator);
    void removeValidator(IPropertyValidator validator);
    void removeValidator(int validatorId);
}
