/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.study.query.SpecimenTablesProvider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class VialDomainKind extends AbstractSpecimenDomainKind
{
    private static final String NAME = "Vial";
    private static final String NAMESPACE_PREFIX = "Vial";

    private static final String ROWID = "RowId";
    private static final String SPECIMENHASH = "SpecimenHash";
    private static final String VOLUME = "Volume";
    private static final String GLOBALUNIQUEID = "GlobalUniqueId";
    private static final String REQUESTABLE = "Requestable";
    private static final String CURRENTLOCATION = "CurrentLocation";
    private static final String PRIMARYVOLUME = "PrimaryVolume";
    private static final String PRIMARYVOLUMEUNITS = "PrimaryVolumeUnits";
    private static final String TOTALCELLCOUNT = "TotalCellCount";
    private static final String SPECIMENID = "SpecimenId";
    private static final String LOCKEDINREQUEST = "LockedInRequest";
    private static final String ATREPOSITORY = "AtRepository";
    private static final String AVAILABLE = "Available";
    private static final String LATESTCOMMENTS = "LatestComments";
    private static final String LATESTQUALITYCOMMENTS = "LatestQualityComments";
    private static final String AVAILABILITYREASON = "AvailabilityReason";
    private static final String PROCESSINGLOCATION = "ProcessingLocation";
    private static final String FIRSTPROCESSEDBYINITIALS = "FirstProcessedByInitials";
    private static final String TUBETYPE = "TubeType";

    private static final List<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> BASE_INDICES;
    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(ROWID, JdbcType.BIGINT, 0, PropertyStorageSpec.Special.PrimaryKey, false, false, null),
            new PropertyStorageSpec(GLOBALUNIQUEID, JdbcType.VARCHAR, 50, false, null),
            new PropertyStorageSpec(VOLUME, JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(SPECIMENHASH, JdbcType.VARCHAR, 256),
            new PropertyStorageSpec(REQUESTABLE, JdbcType.BOOLEAN, 0),
            new PropertyStorageSpec(CURRENTLOCATION, JdbcType.INTEGER, 0, true, null),
            new PropertyStorageSpec(ATREPOSITORY, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(LOCKEDINREQUEST, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(AVAILABLE, JdbcType.BOOLEAN, 0, false, false),
            new PropertyStorageSpec(PROCESSINGLOCATION, JdbcType.INTEGER, 0),
            new PropertyStorageSpec(SPECIMENID, JdbcType.BIGINT, 0, false, null),
            new PropertyStorageSpec(PRIMARYVOLUME, JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(PRIMARYVOLUMEUNITS, JdbcType.VARCHAR, 20),
            new PropertyStorageSpec(FIRSTPROCESSEDBYINITIALS, JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(AVAILABILITYREASON, JdbcType.VARCHAR, 256),
            new PropertyStorageSpec(TOTALCELLCOUNT, JdbcType.DOUBLE, 0),
            new PropertyStorageSpec(LATESTCOMMENTS, JdbcType.VARCHAR, 500),
            new PropertyStorageSpec(LATESTQUALITYCOMMENTS, JdbcType.VARCHAR, 500),
            new PropertyStorageSpec(TUBETYPE, JdbcType.VARCHAR, 64, "The type of vial.")
        };
        BASE_PROPERTIES = Arrays.asList(props);

        PropertyStorageSpec.Index[] indices =
        {
            new PropertyStorageSpec.Index(false, SPECIMENHASH),
            new PropertyStorageSpec.Index(false, CURRENTLOCATION),
            new PropertyStorageSpec.Index(true, GLOBALUNIQUEID),
            new PropertyStorageSpec.Index(false, SPECIMENID)
        };
        BASE_INDICES = new HashSet<>(Arrays.asList(indices));

    }

    private String _specimenDomainURI;

    public VialDomainKind()
    {
        this(null);
    }

    public VialDomainKind(String specimenDomainURI)
    {
        super();
        _specimenDomainURI = specimenDomainURI;
    }

    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        Set<PropertyStorageSpec> specs = new LinkedHashSet<>(BASE_PROPERTIES);
        return specs;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
        Set<PropertyStorageSpec.Index> indices = new HashSet<>(BASE_INDICES);
        return indices;
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container, SpecimenTablesProvider provider)
    {
        Set<PropertyStorageSpec.ForeignKey> foreignKeys = new HashSet<>();
        foreignKeys.add(new PropertyStorageSpec.ForeignKey(CURRENTLOCATION, "study", "Site", "RowId", null, false));
        foreignKeys.add(new PropertyStorageSpec.ForeignKey(SPECIMENID, "study", "Specimen", "RowId", _specimenDomainURI, true));
        setForeignKeyTableInfos(container, foreignKeys, provider);
        return foreignKeys;
    }

    protected String getNamespacePrefix()
    {
        return NAMESPACE_PREFIX;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        TreeSet<String> ret = new TreeSet<>();
        for (PropertyStorageSpec spec : BASE_PROPERTIES)
            ret.add(spec.getName());
        return ret;
    }

    @Override
    public Set<PropertyStorageSpec> getPropertySpecsFromTemplate(SpecimenTablesTemplate template)
    {
        return template.getExtraVialProperties();
    }
}
