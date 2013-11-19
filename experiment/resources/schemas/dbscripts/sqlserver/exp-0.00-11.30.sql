/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

/* exp-0.00-11.20.sql */

/*
 *  Creates experiment annotation tables in the exp schema base on FuGE-OM types
 *
 *  Author Peter Hussey
 *  LabKey Software
 */

CREATE SCHEMA exp;
GO

EXEC sp_addtype 'LSIDtype', 'NVARCHAR(300)';
GO

CREATE TABLE exp.Protocol
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    ProtocolDescription NTEXT NULL,
    ApplicationType NVARCHAR (50) NULL,
    MaxInputMaterialPerInstance INT NULL,
    MaxInputDataPerInstance INT NULL,
    OutputMaterialPerInstance INT NULL,
    OutputDataPerInstance INT NULL,
    OutputMaterialType NVARCHAR (50) NULL,
    OutputDataType NVARCHAR (50) NULL,
    Instrument NVARCHAR (200) NULL,
    Software NVARCHAR (200) NULL,
    ContactId NVARCHAR (100) NULL,
    Created DATETIME NULL,
    EntityId UNIQUEIDENTIFIER NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container EntityId NOT NULL,

    CONSTRAINT PK_Protocol PRIMARY KEY (RowId),
    CONSTRAINT UQ_Protocol_LSID UNIQUE (LSID)
);

CREATE TABLE exp.Experiment
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    Hypothesis TEXT NULL,
    ContactId NVARCHAR (100) NULL,
    ExperimentDescriptionURL NVARCHAR (200) NULL,
    Comments TEXT NULL,
    EntityId UNIQUEIDENTIFIER NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container EntityId NOT NULL,
    Hidden BIT NOT NULL DEFAULT 0,
    BatchProtocolId INT NULL,

    CONSTRAINT PK_Experiment PRIMARY KEY (RowId),
    CONSTRAINT UQ_Experiment_LSID UNIQUE (LSID),
    CONSTRAINT FK_Experiment_BatchProtocolId FOREIGN KEY (BatchProtocolId) REFERENCES exp.Protocol (RowId)
);
CREATE INDEX IX_Experiment_Container ON exp.Experiment(Container);
CREATE INDEX IDX_Experiment_BatchProtocolId ON exp.Experiment(BatchProtocolId);

CREATE TABLE exp.ExperimentRun
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (100) NULL,
    ProtocolLSID LSIDtype NOT NULL,
    Comments NTEXT NULL,
    EntityId UNIQUEIDENTIFIER NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container EntityId NOT NULL,
    FilePathRoot NVARCHAR(500),

    CONSTRAINT PK_ExperimentRun PRIMARY KEY NONCLUSTERED (RowId),
    CONSTRAINT UQ_ExperimentRun_LSID UNIQUE (LSID),
    CONSTRAINT FK_ExperimentRun_Protocol FOREIGN KEY (ProtocolLSID) REFERENCES exp.Protocol (LSID)
);
CREATE CLUSTERED INDEX IX_CL_ExperimentRun_Container ON exp.ExperimentRun(Container);
CREATE INDEX IX_ExperimentRun_ProtocolLSID ON exp.ExperimentRun(ProtocolLSID);

CREATE TABLE exp.ProtocolApplication
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    CpasType NVARCHAR (50) NULL,
    ProtocolLSID LSIDtype NOT NULL,
    ActivityDate DATETIME NULL,
    Comments NVARCHAR (2000) NULL,
    RunId INT NOT NULL,
    ActionSequence INT NOT NULL,

    CONSTRAINT PK_ProtocolApplication PRIMARY KEY NONCLUSTERED (RowId),
    CONSTRAINT UQ_ProtocolApp_LSID UNIQUE (LSID),
    CONSTRAINT FK_ProtocolApplication_ExperimentRun FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT FK_ProtocolApplication_Protocol FOREIGN KEY (ProtocolLSID) REFERENCES exp.Protocol (LSID)
);
CREATE CLUSTERED INDEX IX_CL_ProtocolApplication_RunId ON exp.ProtocolApplication(RunId);
CREATE INDEX IX_ProtocolApplication_ProtocolLSID ON exp.ProtocolApplication(ProtocolLSID);

CREATE TABLE exp.Data
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    CpasType NVARCHAR (50) NULL,
    SourceApplicationId INT NULL,
    DataFileUrl NVARCHAR (400) NULL,
    RunId INT NULL,
    Created DATETIME NOT NULL,
    CreatedBy INT,
    Modified DATETIME,
    ModifiedBy INT,
    Container EntityId NOT NULL,

    CONSTRAINT PK_Data PRIMARY KEY NONCLUSTERED (RowId),
    CONSTRAINT UQ_Data_LSID UNIQUE (LSID),
    CONSTRAINT FK_Data_ExperimentRun FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT FK_Data_ProtocolApplication FOREIGN KEY (SourceApplicationID) REFERENCES exp.ProtocolApplication (RowId),
    CONSTRAINT FK_Data_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
CREATE CLUSTERED INDEX IX_CL_Data_RunId ON exp.Data(RunId);
CREATE INDEX IX_Data_Container ON exp.Data(Container);
CREATE INDEX IX_Data_SourceApplicationId ON exp.Data(SourceApplicationId);
CREATE INDEX IX_Data_DataFileUrl ON exp.Data(DataFileUrl);

/*
   Make PropertyDescriptor consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

   We're somewhat merging to concepts here.

   A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
   A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)
*/
CREATE TABLE exp.PropertyDescriptor
(
    PropertyId INT IDENTITY (1, 1) NOT NULL,
    PropertyURI NVARCHAR (200) NOT NULL,
    OntologyURI NVARCHAR (200) NULL,
    Name NVARCHAR (200) NULL,
    Description NTEXT NULL,
    RangeURI NVARCHAR (200) NOT NULL DEFAULT ('http://www.w3.org/2001/XMLSchema#string'),
    ConceptURI NVARCHAR (200) NULL,
    Label NVARCHAR (200) NULL,
    SearchTerms NVARCHAR (1000) NULL,
    SemanticType NVARCHAR (200) NULL,
    Format NVARCHAR (50) NULL,
    Container ENTITYID NOT NULL,
    Project ENTITYID NOT NULL,

    LookupContainer ENTITYID,
    LookupSchema VARCHAR(50),
    LookupQuery VARCHAR(50),
    DefaultValueType NVARCHAR(50),
    Hidden BIT NOT NULL DEFAULT 0,
    MvEnabled BIT NOT NULL DEFAULT 0,
    ImportAliases NVARCHAR(200),
    URL NVARCHAR(200),
    ShownInInsertView BIT NOT NULL DEFAULT 1,
    ShownInUpdateView BIT NOT NULL DEFAULT 1,
    ShownInDetailsView BIT NOT NULL DEFAULT 1,
    Dimension BIT NOT NULL DEFAULT '0',
    Measure BIT NOT NULL DEFAULT '0',

    CONSTRAINT PK_PropertyDescriptor PRIMARY KEY NONCLUSTERED (PropertyId),
    CONSTRAINT UQ_PropertyDescriptor UNIQUE CLUSTERED (Project, PropertyURI),
    CONSTRAINT UQ_PropertyURIContainer UNIQUE (PropertyURI, Container)
);
CREATE INDEX IX_PropertyDescriptor_Container ON exp.PropertyDescriptor(Container);

CREATE TABLE exp.DataInput
(
    DataId INT NOT NULL,
    TargetApplicationId INT NOT NULL,
    Role VARCHAR(50) NOT NULL,

    CONSTRAINT PK_DataInput PRIMARY KEY (DataId,TargetApplicationId),
    CONSTRAINT FK_DataInputData_Data FOREIGN KEY (DataId) REFERENCES exp.Data (RowId),
    CONSTRAINT FK_DataInput_ProtocolApplication FOREIGN KEY (TargetApplicationId) REFERENCES exp.ProtocolApplication (RowId)
);
CREATE INDEX IX_DataInput_TargetApplicationId ON exp.DataInput(TargetApplicationId);
CREATE INDEX IDX_DataInput_Role ON exp.DataInput(Role);

CREATE TABLE exp.Material
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    CpasType NVARCHAR (200) NULL,
    SourceApplicationId INT NULL,
    RunId INT NULL,
    Created DATETIME NOT NULL,
    Container EntityId NOT NULL,

    CreatedBy INT,
    ModifiedBy INT,
    Modified DATETIME,
    LastIndexed DATETIME,

    CONSTRAINT PK_Material PRIMARY KEY NONCLUSTERED (RowId),
    CONSTRAINT UQ_Material_LSID UNIQUE (LSID),
    CONSTRAINT FK_Material_ExperimentRun FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT FK_Material_ProtocolApplication FOREIGN KEY (SourceApplicationID) REFERENCES exp.ProtocolApplication (RowId),
    CONSTRAINT FK_Material_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
CREATE CLUSTERED INDEX IX_CL_Material_RunId ON exp.Material(RunId);
CREATE INDEX IX_Material_Container ON exp.Material(Container);
CREATE INDEX IX_Material_SourceApplicationId ON exp.Material(SourceApplicationId);
CREATE INDEX IX_Material_CpasType ON exp.Material(CpasType);
CREATE INDEX IDX_Material_LSID ON exp.Material(LSID);

CREATE TABLE exp.MaterialInput
(
    MaterialId INT NOT NULL,
    TargetApplicationId INT NOT NULL,
    Role VARCHAR(50) NOT NULL,

    CONSTRAINT PK_MaterialInput PRIMARY KEY (MaterialId, TargetApplicationId),
    CONSTRAINT FK_MaterialInput_Material FOREIGN KEY (MaterialId) REFERENCES exp.Material (RowId),
    CONSTRAINT FK_MaterialInput_ProtocolApplication FOREIGN KEY (TargetApplicationId) REFERENCES exp.ProtocolApplication (RowId)
);
CREATE INDEX IX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId);
CREATE INDEX IDX_MaterialInput_Role ON exp.MaterialInput(Role);

CREATE TABLE exp.MaterialSource
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    Name NVARCHAR(50) NOT NULL,
    LSID LSIDtype NOT NULL,
    MaterialLSIDPrefix NVARCHAR(200) NULL,
    Description NTEXT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container EntityId NOT NULL,

    IdCol1 NVARCHAR(200) NULL,
    IdCol2 NVARCHAR(200) NULL,
    IdCol3 NVARCHAR(200) NULL,
    ParentCol NVARCHAR(200) NULL

    CONSTRAINT PK_MaterialSource PRIMARY KEY (RowId),
    CONSTRAINT UQ_MaterialSource_LSID UNIQUE (LSID),
);
CREATE INDEX IX_MaterialSource_Container ON exp.MaterialSource(Container);

CREATE TABLE exp.Object
(
    ObjectId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    ObjectURI LSIDType NOT NULL,
    OwnerObjectId INT NULL,

    CONSTRAINT PK_Object PRIMARY KEY NONCLUSTERED (ObjectId),
    CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId) REFERENCES exp.Object (ObjectId),
    CONSTRAINT UQ_Object UNIQUE (ObjectURI),
    CONSTRAINT FK_Object_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
CREATE CLUSTERED INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);
CREATE INDEX IX_Object_OwnerObjectId ON exp.Object(OwnerObjectId);

-- todo this index is in pqsql script.  Needed here?
-- CREATE INDEX IDX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId);

CREATE TABLE exp.ObjectProperty
(
    ObjectId INT NOT NULL,  -- FK exp.Object
    PropertyId INT NOT NULL, -- FK exp.PropertyDescriptor
    TypeTag CHAR(1) NOT NULL, -- s string, f float, d datetime, t text
    FloatValue FLOAT NULL,
    DateTimeValue DATETIME NULL,
    StringValue NVARCHAR(4000) NULL,
    MvIndicator NVARCHAR(50) NULL,

    CONSTRAINT PK_ObjectProperty PRIMARY KEY CLUSTERED (ObjectId, PropertyId),
    CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId) REFERENCES exp.Object (ObjectId),
    CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
);
CREATE INDEX IDX_ObjectProperty_PropertyId ON exp.ObjectProperty(PropertyId);

CREATE TABLE exp.ProtocolAction
(
    RowId INT IDENTITY (10, 10) NOT NULL,
    ParentProtocolId INT NOT NULL,
    ChildProtocolId INT NOT NULL,
    Sequence INT NOT NULL,

    CONSTRAINT PK_ProtocolAction PRIMARY KEY (RowId),
    CONSTRAINT UQ_ProtocolAction UNIQUE (ParentProtocolId, ChildProtocolId, Sequence),
    CONSTRAINT FK_ProtocolAction_Parent_Protocol FOREIGN KEY (ParentProtocolId) REFERENCES exp.Protocol (RowId),
    CONSTRAINT FK_ProtocolAction_Child_Protocol FOREIGN KEY (ChildProtocolId) REFERENCES exp.Protocol (RowId)
);
CREATE INDEX IX_ProtocolAction_ChildProtocolId ON exp.ProtocolAction(ChildProtocolId);

CREATE TABLE exp.ProtocolActionPredecessor
(
    ActionId INT NOT NULL,
    PredecessorId INT NOT NULL,

    CONSTRAINT PK_ActionPredecessor PRIMARY KEY (ActionId, PredecessorId),
    CONSTRAINT FK_ActionPredecessor_Action_ProtocolAction FOREIGN KEY (ActionId) REFERENCES exp.ProtocolAction (RowId),
    CONSTRAINT FK_ActionPredecessor_Predecessor_ProtocolAction FOREIGN KEY (PredecessorId) REFERENCES exp.ProtocolAction (RowId)
);
CREATE INDEX IX_ProtocolActionPredecessor_PredecessorId ON exp.ProtocolActionPredecessor(PredecessorId);

CREATE TABLE exp.ProtocolParameter
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    ProtocolId INT NOT NULL,
    Name NVARCHAR (200) NULL,
    ValueType NVARCHAR(50) NULL,
    StringValue NVARCHAR (4000) NULL,
    IntegerValue INT NULL,
    DoubleValue FLOAT NULL,
    DateTimeValue DATETIME NULL,
    OntologyEntryURI NVARCHAR (200) NULL,

    CONSTRAINT PK_ProtocolParameter PRIMARY KEY (RowId),
    CONSTRAINT UQ_ProtocolParameter_Ord UNIQUE (ProtocolId, Name),
    CONSTRAINT FK_ProtocolParameter_Protocol FOREIGN KEY (ProtocolId) REFERENCES exp.Protocol (RowId)
);
CREATE INDEX IX_ProtocolParameter_ProtocolId ON exp.ProtocolParameter(ProtocolId);

CREATE TABLE exp.ProtocolApplicationParameter
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    ProtocolApplicationId INT NOT NULL,
    Name NVARCHAR (200) NULL,
    ValueType NVARCHAR(50) NULL,
    StringValue NTEXT NULL,
    IntegerValue INT NULL,
    DoubleValue FLOAT NULL,
    DateTimeValue DATETIME NULL,
    OntologyEntryURI NVARCHAR (200) NULL,

    CONSTRAINT PK_ProtocolAppParam PRIMARY KEY (RowId),
    CONSTRAINT UQ_ProtocolAppParam_Ord UNIQUE (ProtocolApplicationId, Name),
    CONSTRAINT FK_ProtocolAppParam_ProtocolApp FOREIGN KEY (ProtocolApplicationId) REFERENCES exp.ProtocolApplication (RowId)
);
CREATE INDEX IX_ProtocolApplicationParameter_AppId ON exp.ProtocolApplicationParameter(ProtocolApplicationId);

CREATE TABLE exp.DomainDescriptor
(
    DomainId INT IDENTITY (1, 1) NOT NULL,
    Name NVARCHAR (200) NULL,
    DomainURI NVARCHAR (200) NOT NULL,
    Description NTEXT NULL,
    Container ENTITYID NOT NULL,
    Project ENTITYID NOT NULL,
    StorageTableName NVARCHAR(100),
    StorageSchemaName NVARCHAR(100),

    CONSTRAINT PK_DomainDescriptor PRIMARY KEY NONCLUSTERED (DomainId),
    CONSTRAINT UQ_DomainDescriptor UNIQUE CLUSTERED (Project, DomainURI),
    CONSTRAINT UQ_DomainURIContainer UNIQUE (DomainURI, Container)
);
CREATE INDEX IX_DomainDescriptor_Container ON exp.DomainDescriptor(Container);

CREATE TABLE exp.PropertyDomain
(
    PropertyId INT NOT NULL,
    DomainId INT NOT NULL,
    Required BIT NOT NULL DEFAULT 0,
    SortOrder INT NOT NULL DEFAULT 0,

    CONSTRAINT PK_PropertyDomain PRIMARY KEY  CLUSTERED (PropertyId, DomainId),
    CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId),
    CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId),
);
CREATE INDEX IX_PropertyDomain_DomainId ON exp.PropertyDomain(DomainID);

CREATE TABLE exp.RunList
(
    ExperimentId INT NOT NULL,
    ExperimentRunId INT NOT NULL,

    CONSTRAINT PK_RunList PRIMARY KEY (ExperimentId, ExperimentRunId),
    CONSTRAINT FK_RunList_ExperimentId FOREIGN KEY (ExperimentId) REFERENCES exp.Experiment(RowId),
    CONSTRAINT FK_RunList_ExperimentRunId FOREIGN KEY (ExperimentRunId) REFERENCES exp.ExperimentRun(RowId)
);
CREATE INDEX IX_RunList_ExperimentRunId ON exp.RunList(ExperimentRunId);

CREATE TABLE exp.ActiveMaterialSource
(
    Container ENTITYID NOT NULL,
    MaterialSourceLSID LSIDtype NOT NULL,

    CONSTRAINT PK_ActiveMaterialSource PRIMARY KEY (Container),
    CONSTRAINT FK_ActiveMaterialSource_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_ActiveMaterialSource_MaterialSourceLSID FOREIGN KEY (MaterialSourceLSID) REFERENCES exp.MaterialSource(LSID)
);
CREATE INDEX IX_ActiveMaterialSource_MaterialSourceLSID ON exp.ActiveMaterialSource(MaterialSourceLSID);

CREATE TABLE exp.list
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    Name NVARCHAR(64) NOT NULL,
    DomainId INT NOT NULL,
    KeyName NVARCHAR(64) NOT NULL,
    KeyType VARCHAR(64) NOT NULL,
    Description NTEXT,
    TitleColumn NVARCHAR(200),
    DiscussionSetting SMALLINT NOT NULL DEFAULT 0,
    AllowDelete BIT NOT NULL DEFAULT 1,
    AllowUpload BIT NOT NULL DEFAULT 1,
    AllowExport BIT NOT NULL DEFAULT 1,
    IndexMetaData BIT NOT NULL DEFAULT 1,

    CONSTRAINT PK_List PRIMARY KEY(RowId),
    CONSTRAINT UQ_LIST UNIQUE(Container, Name),
    CONSTRAINT FK_List_DomainId FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor(DomainId)
);
CREATE INDEX IDX_List_DomainId ON exp.List(DomainId);

CREATE TABLE exp.IndexInteger
(
    ListId INT NOT NULL,
    "Key" INT NOT NULL,
    ObjectId INT NULL,
    EntityId ENTITYID, -- Used for discussions & attachments

    CONSTRAINT PK_IndexInteger PRIMARY KEY(ListId, "Key"),
    CONSTRAINT FK_IndexInteger_List FOREIGN KEY (ListId) REFERENCES exp.List(RowId),
    CONSTRAINT FK_IndexInteger_Object FOREIGN KEY (ObjectId) REFERENCES exp.Object(ObjectId)
);
CREATE INDEX IDX_IndexInteger_ObjectId ON exp.IndexInteger(ObjectId);

CREATE TABLE exp.IndexVarchar
(
    ListId INT NOT NULL,
    "Key" VARCHAR(300) NOT NULL,
    ObjectId INT NULL,
    EntityId ENTITYID, -- Used for discussions & attachments

    CONSTRAINT PK_IndexVarchar PRIMARY KEY(ListId, "Key"),
    CONSTRAINT FK_IndexVarchar_List FOREIGN KEY (ListId) REFERENCES exp.List(RowId),
    CONSTRAINT FK_IndexVarchar_Object FOREIGN KEY (ObjectId) REFERENCES exp.Object(ObjectId)
);
CREATE INDEX IDX_IndexVarchar_ObjectId ON exp.IndexVarchar(ObjectId);

CREATE TABLE exp.PropertyValidator
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Name NVARCHAR(50) NOT NULL,
    Description NVARCHAR(200),
    TypeURI NVARCHAR(200) NOT NULL,
    Expression TEXT,
    Properties TEXT,
    ErrorMessage TEXT,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId)
);

CREATE TABLE exp.ValidatorReference
(
    ValidatorId INT NOT NULL,
    PropertyId INT NOT NULL,

    CONSTRAINT PK_ValidatorReference PRIMARY KEY (ValidatorId, PropertyId),
    CONSTRAINT FK_PropertyValidator_ValidatorId FOREIGN KEY (ValidatorId) REFERENCES exp.PropertyValidator (RowId),
    CONSTRAINT FK_PropertyDescriptor_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
);

CREATE TABLE exp.ConditionalFormat
(
    RowId INT IDENTITY(1,1) NOT NULL,
    SortOrder INT NOT NULL,
    PropertyId INT NOT NULL,
    Filter NVARCHAR(500) NOT NULL,
    Bold BIT NOT NULL,
    Italic BIT NOT NULL,
    Strikethrough BIT NOT NULL,
    TextColor NVARCHAR(10),
    BackgroundColor NVARCHAR(10),

    CONSTRAINT PK_ConditionalFormat_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_ConditionalFormat_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId),
    CONSTRAINT UQ_ConditionalFormat_PropertyId_SortOrder UNIQUE (PropertyId, SortOrder)
);
CREATE INDEX IDX_ConditionalFormat_PropertyId ON exp.ConditionalFormat(PropertyId);

GO
-- Create procs used by Ontology Manager
CREATE PROCEDURE exp.getObjectProperties(@container ENTITYID, @lsid LSIDType) AS
BEGIN
    SELECT * FROM exp.ObjectPropertiesView
    WHERE Container = @container AND ObjectURI = @lsid
END

GO
CREATE PROCEDURE exp.ensureObject(@container ENTITYID, @lsid LSIDType, @ownerObjectId INTEGER) AS
BEGIN
    DECLARE @objectid AS INTEGER
    SET NOCOUNT ON
    BEGIN TRANSACTION
        SELECT @objectid = ObjectId FROM exp.Object WHERE Container=@container AND ObjectURI=@lsid
        IF (@objectid IS NULL)
        BEGIN
            INSERT INTO exp.Object (Container, ObjectURI, OwnerObjectId) VALUES (@container, @lsid, @ownerObjectId)
            SELECT @objectid = @@identity
        END
    COMMIT
    SELECT @objectid
END

GO
CREATE PROCEDURE exp.deleteObject(@container ENTITYID, @lsid LSIDType) AS
BEGIN
    SET NOCOUNT ON
        DECLARE @objectid INTEGER
        SELECT @objectid = ObjectId FROM exp.Object WHERE Container=@container AND ObjectURI=@lsid
        IF (@objectid IS NULL)
            RETURN
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE ObjectId IN
            (SELECT ObjectId FROM exp.Object WHERE OwnerObjectId = @objectid)
        DELETE exp.ObjectProperty WHERE ObjectId = @objectid
        DELETE exp.Object WHERE OwnerObjectId = @objectid
        DELETE exp.Object WHERE ObjectId = @objectid
    COMMIT
END

GO
-- internal methods
CREATE PROCEDURE exp._insertFloatProperty(@objectid INTEGER, @propid INTEGER, @float FLOAT) AS
BEGIN
    IF (@propid IS NULL OR @objectid IS NULL) RETURN
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue)
    VALUES (@objectid, @propid, 'f', @float)
END

GO
CREATE PROCEDURE exp._insertDateTimeProperty(@objectid INTEGER, @propid INTEGER, @datetime DATETIME) AS
BEGIN
    IF (@propid IS NULL OR @objectid IS NULL) RETURN
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, DateTimeValue)
    VALUES (@objectid, @propid, 'd', @datetime)
END

GO
CREATE PROCEDURE exp._insertStringProperty(@objectid INTEGER, @propid INTEGER, @string VARCHAR(400)) AS
BEGIN
    IF (@propid IS NULL OR @objectid IS NULL) RETURN
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, StringValue)
    VALUES (@objectid, @propid, 's', @string)
END

GO
--
-- Set the same property on multiple objects (e.g. impoirt a column of datea)
--
-- fast method for importing ObjectProperties (need to wrap with datalayer code)
--

CREATE PROCEDURE exp.setFloatProperties(@propertyid INTEGER,
    @objectid1 INTEGER, @float1 FLOAT,
    @objectid2 INTEGER, @float2 FLOAT,
    @objectid3 INTEGER, @float3 FLOAT,
    @objectid4 INTEGER, @float4 FLOAT,
    @objectid5 INTEGER, @float5 FLOAT,
    @objectid6 INTEGER, @float6 FLOAT,
    @objectid7 INTEGER, @float7 FLOAT,
    @objectid8 INTEGER, @float8 FLOAT,
    @objectid9 INTEGER, @float9 FLOAT,
    @objectid10 INTEGER, @float10 FLOAT
    ) AS
BEGIN
    SET NOCOUNT ON
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
        EXEC exp._insertFloatProperty @objectid1, @propertyid, @float1
        EXEC exp._insertFloatProperty @objectid2, @propertyid, @float2
        EXEC exp._insertFloatProperty @objectid3, @propertyid, @float3
        EXEC exp._insertFloatProperty @objectid4, @propertyid, @float4
        EXEC exp._insertFloatProperty @objectid5, @propertyid, @float5
        EXEC exp._insertFloatProperty @objectid6, @propertyid, @float6
        EXEC exp._insertFloatProperty @objectid7, @propertyid, @float7
        EXEC exp._insertFloatProperty @objectid8, @propertyid, @float8
        EXEC exp._insertFloatProperty @objectid9, @propertyid, @float9
        EXEC exp._insertFloatProperty @objectid10, @propertyid, @float10
    COMMIT
END

GO
CREATE PROCEDURE exp.setStringProperties(@propertyid INTEGER,
    @objectid1 INTEGER, @string1 VARCHAR(400),
    @objectid2 INTEGER, @string2 VARCHAR(400),
    @objectid3 INTEGER, @string3 VARCHAR(400),
    @objectid4 INTEGER, @string4 VARCHAR(400),
    @objectid5 INTEGER, @string5 VARCHAR(400),
    @objectid6 INTEGER, @string6 VARCHAR(400),
    @objectid7 INTEGER, @string7 VARCHAR(400),
    @objectid8 INTEGER, @string8 VARCHAR(400),
    @objectid9 INTEGER, @string9 VARCHAR(400),
    @objectid10 INTEGER, @string10 VARCHAR(400)
    ) AS
BEGIN
    SET NOCOUNT ON
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
        EXEC exp._insertStringProperty @objectid1, @propertyid, @string1
        EXEC exp._insertStringProperty @objectid2, @propertyid, @string2
        EXEC exp._insertStringProperty @objectid3, @propertyid, @string3
        EXEC exp._insertStringProperty @objectid4, @propertyid, @string4
        EXEC exp._insertStringProperty @objectid5, @propertyid, @string5
        EXEC exp._insertStringProperty @objectid6, @propertyid, @string6
        EXEC exp._insertStringProperty @objectid7, @propertyid, @string7
        EXEC exp._insertStringProperty @objectid8, @propertyid, @string8
        EXEC exp._insertStringProperty @objectid9, @propertyid, @string9
        EXEC exp._insertStringProperty @objectid10, @propertyid, @string10
    COMMIT
END

GO
CREATE PROCEDURE exp.setDateTimeProperties(@propertyid INTEGER,
    @objectid1 INTEGER, @datetime1 DATETIME,
    @objectid2 INTEGER, @datetime2 DATETIME,
    @objectid3 INTEGER, @datetime3 DATETIME,
    @objectid4 INTEGER, @datetime4 DATETIME,
    @objectid5 INTEGER, @datetime5 DATETIME,
    @objectid6 INTEGER, @datetime6 DATETIME,
    @objectid7 INTEGER, @datetime7 DATETIME,
    @objectid8 INTEGER, @datetime8 DATETIME,
    @objectid9 INTEGER, @datetime9 DATETIME,
    @objectid10 INTEGER, @datetime10 DATETIME
    ) AS
BEGIN
    SET NOCOUNT ON
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
        EXEC exp._insertDateTimeProperty @objectid1, @propertyid, @datetime1
        EXEC exp._insertDateTimeProperty @objectid2, @propertyid, @datetime2
        EXEC exp._insertDateTimeProperty @objectid3, @propertyid, @datetime3
        EXEC exp._insertDateTimeProperty @objectid4, @propertyid, @datetime4
        EXEC exp._insertDateTimeProperty @objectid5, @propertyid, @datetime5
        EXEC exp._insertDateTimeProperty @objectid6, @propertyid, @datetime6
        EXEC exp._insertDateTimeProperty @objectid7, @propertyid, @datetime7
        EXEC exp._insertDateTimeProperty @objectid8, @propertyid, @datetime8
        EXEC exp._insertDateTimeProperty @objectid9, @propertyid, @datetime9
        EXEC exp._insertDateTimeProperty @objectid10, @propertyid, @datetime10
    COMMIT
END

GO
CREATE PROCEDURE exp.deleteObjectById(@container ENTITYID, @objectid INTEGER) AS
BEGIN
    SET NOCOUNT ON
        SELECT @objectid = ObjectId FROM exp.Object WHERE Container=@container AND ObjectId=@objectid
        IF (@objectid IS NULL)
            RETURN
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE ObjectId IN
            (SELECT ObjectId FROM exp.Object WHERE OwnerObjectId = @objectid)
        DELETE exp.ObjectProperty WHERE ObjectId = @objectid
        DELETE exp.Object WHERE OwnerObjectId = @objectid
        DELETE exp.Object WHERE ObjectId = @objectid
    COMMIT
END

/* exp-11.20-11.30.sql */

CREATE INDEX IDX_Protocol_Container ON exp.Protocol (Container)
GO