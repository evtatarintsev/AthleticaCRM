// Сгенерировано задачей :server:generateWebContracts из маршрутов сервера и схем shared.
// Не редактировать вручную: после изменения схем выполните `npm run contracts`.

import { z } from "zod";

export const ClientIdSchema = z.uuid().brand<"ClientId">();
export type ClientId = z.output<typeof ClientIdSchema>;

export const AddClientNoteRequestSchema = z
  .object({
    clientId: ClientIdSchema,
    text: z.string().max(2000),
  })
  .readonly();
export type AddClientNoteRequest = z.output<typeof AddClientNoteRequestSchema>;

export const GroupIdSchema = z.uuid().brand<"GroupId">();
export type GroupId = z.output<typeof GroupIdSchema>;

export const AddClientsToGroupRequestSchema = z
  .object({
    clientIds: z.array(ClientIdSchema).readonly(),
    groupId: GroupIdSchema,
  })
  .readonly();
export type AddClientsToGroupRequest = z.output<typeof AddClientsToGroupRequestSchema>;

export const CurrencySchema = z.enum(["RUB", "USD", "EUR", "KZT", "BYN", "UAH"]);
export type Currency = z.output<typeof CurrencySchema>;

export const MoneySchema = z
  .object({
    minorUnits: z.int(),
    currency: CurrencySchema,
  })
  .readonly();
export type Money = z.output<typeof MoneySchema>;

export const AdjustBalanceRequestSchema = z
  .object({
    clientId: ClientIdSchema,
    amount: MoneySchema,
    note: z.string(),
  })
  .readonly();
export type AdjustBalanceRequest = z.output<typeof AdjustBalanceRequestSchema>;

export const ArchiveClientRequestSchema = z
  .object({
    clientIds: z.array(ClientIdSchema).readonly(),
  })
  .readonly();
export type ArchiveClientRequest = z.output<typeof ArchiveClientRequestSchema>;

export const TariffPlanIdSchema = z.uuid().brand<"TariffPlanId">();
export type TariffPlanId = z.output<typeof TariffPlanIdSchema>;

export const ArchiveTariffPlanRequestSchema = z
  .object({
    id: TariffPlanIdSchema,
    archived: z.boolean(),
  })
  .readonly();
export type ArchiveTariffPlanRequest = z.output<typeof ArchiveTariffPlanRequestSchema>;

export const EmployeeIdSchema = z.uuid().brand<"EmployeeId">();
export type EmployeeId = z.output<typeof EmployeeIdSchema>;

export const TaskIdSchema = z.uuid().brand<"TaskId">();
export type TaskId = z.output<typeof TaskIdSchema>;

export const AssignTaskRequestSchema = z
  .object({
    taskIds: z.array(TaskIdSchema).readonly(),
    assigneeId: EmployeeIdSchema,
  })
  .readonly();
export type AssignTaskRequest = z.output<typeof AssignTaskRequestSchema>;

export const ClientDocIdSchema = z.uuid().brand<"ClientDocId">();
export type ClientDocId = z.output<typeof ClientDocIdSchema>;

export const UploadIdSchema = z.uuid().brand<"UploadId">();
export type UploadId = z.output<typeof UploadIdSchema>;

export const AttachClientDocRequestSchema = z
  .object({
    docId: ClientDocIdSchema,
    clientId: ClientIdSchema,
    uploadId: UploadIdSchema,
    name: z.string(),
  })
  .readonly();
export type AttachClientDocRequest = z.output<typeof AttachClientDocRequestSchema>;

export const AttachTaskUploadRequestSchema = z
  .object({
    taskId: TaskIdSchema,
    uploadId: UploadIdSchema,
  })
  .readonly();
export type AttachTaskUploadRequest = z.output<typeof AttachTaskUploadRequestSchema>;

export const AuditLogItemSchema = z
  .object({
    id: z.uuid(),
    userId: z.uuid().nullable(),
    username: z.string(),
    actionType: z.string(),
    entityType: z.string().nullable(),
    entityId: z.uuid().nullable(),
    data: z.string().nullable(),
    ipAddress: z.string().nullable(),
    createdAt: z.string(),
  })
  .readonly();
export type AuditLogItem = z.output<typeof AuditLogItemSchema>;

export const UserIdSchema = z.uuid().brand<"UserId">();
export type UserId = z.output<typeof UserIdSchema>;

export const AuditLogListRequestSchema = z
  .object({
    page: z.int32().optional(),
    pageSize: z.int32().optional(),
    actionType: z.string().nullable().optional(),
    userId: UserIdSchema.nullable().optional(),
    entityType: z.string().nullable().optional(),
    from: z.string().nullable().optional(),
    to: z.string().nullable().optional(),
  })
  .readonly();
export type AuditLogListRequest = z.output<typeof AuditLogListRequestSchema>;

export const AuditLogListResponseSchema = z
  .object({
    items: z.array(AuditLogItemSchema).readonly(),
    total: z.int(),
    page: z.int32(),
    pageSize: z.int32(),
  })
  .readonly();
export type AuditLogListResponse = z.output<typeof AuditLogListResponseSchema>;

export const AuthBranchesRequestSchema = z
  .object({
    username: z.string(),
    password: z.string(),
  })
  .readonly();
export type AuthBranchesRequest = z.output<typeof AuthBranchesRequestSchema>;

export const BranchIdSchema = z.uuid().brand<"BranchId">();
export type BranchId = z.output<typeof BranchIdSchema>;

export const BranchDetailResponseSchema = z
  .object({
    id: BranchIdSchema,
    name: z.string(),
  })
  .readonly();
export type BranchDetailResponse = z.output<typeof BranchDetailResponseSchema>;

export const AuthBranchesResponseSchema = z
  .object({
    branches: z.array(BranchDetailResponseSchema).readonly(),
  })
  .readonly();
export type AuthBranchesResponse = z.output<typeof AuthBranchesResponseSchema>;

export const OrgInfoSchema = z
  .object({
    name: z.string(),
    balance: MoneySchema.nullable(),
  })
  .readonly();
export type OrgInfo = z.output<typeof OrgInfoSchema>;

export const UserPermissionSchema = z.enum(["CAN_MANAGE_ORG_BALANCE", "CAN_VIEW_CLIENT_BALANCE", "CAN_VIEW_ALL_TASKS", "CAN_MANAGE_TASKS"]);
export type UserPermission = z.output<typeof UserPermissionSchema>;

export const AuthMeResponseSchema = z
  .object({
    id: UserIdSchema,
    employeeId: EmployeeIdSchema,
    username: z.string(),
    name: z.string(),
    avatarId: UploadIdSchema.nullable(),
    orgInfo: OrgInfoSchema,
    currentBranch: BranchDetailResponseSchema,
    permissions: z.array(UserPermissionSchema).readonly(),
  })
  .readonly();
export type AuthMeResponse = z.output<typeof AuthMeResponseSchema>;

export const InstantSchema = z.iso.datetime({ offset: true }).brand<"Instant">();
export type Instant = z.output<typeof InstantSchema>;

export const PerformedBySchema = z
  .object({
    id: z.uuid(),
    name: z.string(),
  })
  .readonly();
export type PerformedBy = z.output<typeof PerformedBySchema>;

export const BalanceJournalEntrySchema = z
  .object({
    id: z.uuid(),
    amount: MoneySchema,
    balanceAfter: MoneySchema,
    operationType: z.string(),
    note: z.string().nullable(),
    performedBy: PerformedBySchema.nullable(),
    createdAt: InstantSchema,
  })
  .readonly();
export type BalanceJournalEntry = z.output<typeof BalanceJournalEntrySchema>;

export const BirthdayWindowSchema = z.enum(["TODAY", "TOMORROW", "WEEK"]);
export type BirthdayWindow = z.output<typeof BirthdayWindowSchema>;

export const BranchCreateRequestSchema = z
  .object({
    id: BranchIdSchema,
    name: z.string(),
  })
  .readonly();
export type BranchCreateRequest = z.output<typeof BranchCreateRequestSchema>;

export const BranchListResponseSchema = z
  .object({
    branches: z.array(BranchDetailResponseSchema).readonly(),
  })
  .readonly();
export type BranchListResponse = z.output<typeof BranchListResponseSchema>;

export const BranchUpdateRequestSchema = z
  .object({
    id: BranchIdSchema,
    name: z.string(),
  })
  .readonly();
export type BranchUpdateRequest = z.output<typeof BranchUpdateRequestSchema>;

export const BulkUpdateTasksResponseSchema = z
  .object({
    updated: z.int32(),
  })
  .readonly();
export type BulkUpdateTasksResponse = z.output<typeof BulkUpdateTasksResponseSchema>;

export const ChangePasswordRequestSchema = z
  .object({
    oldPassword: z.string(),
    newPassword: z.string(),
  })
  .readonly();
export type ChangePasswordRequest = z.output<typeof ChangePasswordRequestSchema>;

export const ChannelConfigInAppSchema = z
  .object({
    type: z.literal("in_app"),
  })
  .readonly();
export type ChannelConfigInApp = z.output<typeof ChannelConfigInAppSchema>;

export const ChannelConfigSmscSmsSchema = z
  .object({
    type: z.literal("smsc_sms"),
    login: z.string(),
    password: z.string(),
  })
  .readonly();
export type ChannelConfigSmscSms = z.output<typeof ChannelConfigSmscSmsSchema>;

export const ChannelConfigTelegramBotSchema = z
  .object({
    type: z.literal("telegram_bot"),
    botToken: z.string(),
  })
  .readonly();
export type ChannelConfigTelegramBot = z.output<typeof ChannelConfigTelegramBotSchema>;

export const ChannelConfigTwilioSmsSchema = z
  .object({
    type: z.literal("twilio_sms"),
    accountSid: z.string(),
    authToken: z.string(),
    from: z.string(),
  })
  .readonly();
export type ChannelConfigTwilioSms = z.output<typeof ChannelConfigTwilioSmsSchema>;

export const ChannelConfigSchema = z.discriminatedUnion("type", [ChannelConfigInAppSchema, ChannelConfigSmscSmsSchema, ChannelConfigTelegramBotSchema, ChannelConfigTwilioSmsSchema]);
export type ChannelConfig = z.output<typeof ChannelConfigSchema>;

export const ChannelIntegrationIdSchema = z.uuid().brand<"ChannelIntegrationId">();
export type ChannelIntegrationId = z.output<typeof ChannelIntegrationIdSchema>;

export const ChannelTypeSchema = z.enum(["SMS", "TELEGRAM", "WHATSAPP", "EMAIL", "MAX", "VK", "IN_APP"]);
export type ChannelType = z.output<typeof ChannelTypeSchema>;

export const ChannelIntegrationSchemaSchema = z
  .object({
    id: ChannelIntegrationIdSchema,
    channelType: ChannelTypeSchema,
    name: z.string(),
    config: ChannelConfigSchema,
    enabled: z.boolean(),
  })
  .readonly();
export type ChannelIntegrationSchema = z.output<typeof ChannelIntegrationSchemaSchema>;

export const ChannelListResponseSchema = z
  .object({
    channels: z.array(ChannelIntegrationSchemaSchema).readonly(),
  })
  .readonly();
export type ChannelListResponse = z.output<typeof ChannelListResponseSchema>;

export const ClientBalanceHistoryRequestSchema = z
  .object({
    id: ClientIdSchema,
  })
  .readonly();
export type ClientBalanceHistoryRequest = z.output<typeof ClientBalanceHistoryRequestSchema>;

export const ClientBalanceHistoryResponseSchema = z
  .object({
    entries: z.array(BalanceJournalEntrySchema).readonly(),
  })
  .readonly();
export type ClientBalanceHistoryResponse = z.output<typeof ClientBalanceHistoryResponseSchema>;

export const ClientContactIdSchema = z.uuid().brand<"ClientContactId">();
export type ClientContactId = z.output<typeof ClientContactIdSchema>;

export const ContactTypeSchema = z.enum(["PHONE", "EMAIL", "TELEGRAM", "VK", "FACEBOOK"]);
export type ContactType = z.output<typeof ContactTypeSchema>;

export const ClientContactInputSchema = z
  .object({
    type: ContactTypeSchema,
    value: z.string(),
  })
  .readonly();
export type ClientContactInput = z.output<typeof ClientContactInputSchema>;

export const ClientContactSchemaSchema = z
  .object({
    id: ClientContactIdSchema,
    type: ContactTypeSchema,
    value: z.string(),
  })
  .readonly();
export type ClientContactSchema = z.output<typeof ClientContactSchemaSchema>;

export const ClientDetailRequestSchema = z
  .object({
    id: ClientIdSchema,
  })
  .readonly();
export type ClientDetailRequest = z.output<typeof ClientDetailRequestSchema>;

export const LocalDateSchema = z.iso.date().brand<"LocalDate">();
export type LocalDate = z.output<typeof LocalDateSchema>;

export const ClientDocSchema = z
  .object({
    id: ClientDocIdSchema,
    uploadId: UploadIdSchema,
    name: z.string(),
    createdAt: InstantSchema,
  })
  .readonly();
export type ClientDoc = z.output<typeof ClientDocSchema>;

export const ClientGroupSchema = z
  .object({
    id: GroupIdSchema,
    name: z.string(),
  })
  .readonly();
export type ClientGroup = z.output<typeof ClientGroupSchema>;

export const ClientStateSchema = z.enum(["ACTIVE", "ARCHIVED"]);
export type ClientState = z.output<typeof ClientStateSchema>;

export const GenderSchema = z.enum(["MALE", "FEMALE"]);
export type Gender = z.output<typeof GenderSchema>;

export const CustomFieldKeySchema = z.string().regex(/^[a-z_]+$/).brand<"CustomFieldKey">();
export type CustomFieldKey = z.output<typeof CustomFieldKeySchema>;

export const CustomFieldValueBoolSchema = z
  .object({
    type: z.literal("bool"),
    fieldKey: CustomFieldKeySchema,
    value: z.boolean(),
  })
  .readonly();
export type CustomFieldValueBool = z.output<typeof CustomFieldValueBoolSchema>;

export const CustomFieldValueDateSchema = z
  .object({
    type: z.literal("date"),
    fieldKey: CustomFieldKeySchema,
    value: LocalDateSchema,
  })
  .readonly();
export type CustomFieldValueDate = z.output<typeof CustomFieldValueDateSchema>;

export const CustomFieldValueNumberSchema = z
  .object({
    type: z.literal("number"),
    fieldKey: CustomFieldKeySchema,
    value: z.number(),
  })
  .readonly();
export type CustomFieldValueNumber = z.output<typeof CustomFieldValueNumberSchema>;

export const CustomFieldValueSelectSchema = z
  .object({
    type: z.literal("select"),
    fieldKey: CustomFieldKeySchema,
    value: z.string(),
  })
  .readonly();
export type CustomFieldValueSelect = z.output<typeof CustomFieldValueSelectSchema>;

export const CustomFieldValueTextSchema = z
  .object({
    type: z.literal("text"),
    fieldKey: CustomFieldKeySchema,
    value: z.string(),
  })
  .readonly();
export type CustomFieldValueText = z.output<typeof CustomFieldValueTextSchema>;

export const CustomFieldValueSchema = z.discriminatedUnion("type", [CustomFieldValueBoolSchema, CustomFieldValueDateSchema, CustomFieldValueNumberSchema, CustomFieldValueSelectSchema, CustomFieldValueTextSchema]);
export type CustomFieldValue = z.output<typeof CustomFieldValueSchema>;

export const LeadSourceIdSchema = z.uuid().brand<"LeadSourceId">();
export type LeadSourceId = z.output<typeof LeadSourceIdSchema>;

export const ClientDetailResponseSchema = z
  .object({
    id: ClientIdSchema,
    name: z.string(),
    avatarId: UploadIdSchema.nullable(),
    birthday: LocalDateSchema.nullable(),
    gender: GenderSchema,
    groups: z.array(ClientGroupSchema).readonly(),
    balance: MoneySchema,
    docs: z.array(ClientDocSchema).readonly(),
    leadSourceId: LeadSourceIdSchema.nullable(),
    customFields: z.array(CustomFieldValueSchema).readonly(),
    contacts: z.array(ClientContactSchemaSchema).readonly(),
    state: ClientStateSchema,
  })
  .readonly();
export type ClientDetailResponse = z.output<typeof ClientDetailResponseSchema>;

export const ClientExportRequestSchema = z
  .object({
    fields: z.array(z.string()).readonly(),
  })
  .readonly();
export type ClientExportRequest = z.output<typeof ClientExportRequestSchema>;

export const ImportTargetBalanceSchema = z
  .object({
    type: z.literal("balance"),
  })
  .readonly();
export type ImportTargetBalance = z.output<typeof ImportTargetBalanceSchema>;

export const ImportTargetBirthdaySchema = z
  .object({
    type: z.literal("birthday"),
  })
  .readonly();
export type ImportTargetBirthday = z.output<typeof ImportTargetBirthdaySchema>;

export const ImportTargetCustomFieldSchema = z
  .object({
    type: z.literal("custom_field"),
    key: CustomFieldKeySchema,
  })
  .readonly();
export type ImportTargetCustomField = z.output<typeof ImportTargetCustomFieldSchema>;

export const ImportTargetGenderSchema = z
  .object({
    type: z.literal("gender"),
  })
  .readonly();
export type ImportTargetGender = z.output<typeof ImportTargetGenderSchema>;

export const ImportTargetLeadSourceSchema = z
  .object({
    type: z.literal("lead_source"),
  })
  .readonly();
export type ImportTargetLeadSource = z.output<typeof ImportTargetLeadSourceSchema>;

export const ImportTargetNameSchema = z
  .object({
    type: z.literal("name"),
  })
  .readonly();
export type ImportTargetName = z.output<typeof ImportTargetNameSchema>;

export const ImportTargetSkipSchema = z
  .object({
    type: z.literal("skip"),
  })
  .readonly();
export type ImportTargetSkip = z.output<typeof ImportTargetSkipSchema>;

export const ImportTargetSchema = z.discriminatedUnion("type", [ImportTargetBalanceSchema, ImportTargetBirthdaySchema, ImportTargetCustomFieldSchema, ImportTargetGenderSchema, ImportTargetLeadSourceSchema, ImportTargetNameSchema, ImportTargetSkipSchema]);
export type ImportTarget = z.output<typeof ImportTargetSchema>;

export const ColumnMappingSchema = z
  .object({
    sourceColumn: z.string(),
    target: ImportTargetSchema,
  })
  .readonly();
export type ColumnMapping = z.output<typeof ColumnMappingSchema>;

export const LeadSourceActionCreateNewSchema = z
  .object({
    type: z.literal("create_new"),
    name: z.string(),
  })
  .readonly();
export type LeadSourceActionCreateNew = z.output<typeof LeadSourceActionCreateNewSchema>;

export const LeadSourceActionSkipSchema = z
  .object({
    type: z.literal("skip"),
  })
  .readonly();
export type LeadSourceActionSkip = z.output<typeof LeadSourceActionSkipSchema>;

export const LeadSourceActionUseExistingSchema = z
  .object({
    type: z.literal("use_existing"),
    id: LeadSourceIdSchema,
  })
  .readonly();
export type LeadSourceActionUseExisting = z.output<typeof LeadSourceActionUseExistingSchema>;

export const LeadSourceActionSchema = z.discriminatedUnion("type", [LeadSourceActionCreateNewSchema, LeadSourceActionSkipSchema, LeadSourceActionUseExistingSchema]);
export type LeadSourceAction = z.output<typeof LeadSourceActionSchema>;

export const ClientImportCommitRequestSchema = z
  .object({
    uploadId: UploadIdSchema,
    columnMapping: z.array(ColumnMappingSchema).readonly(),
    defaultGender: GenderSchema,
    genderMapping: z.record(z.string(), GenderSchema).readonly().optional(),
    leadSourceMapping: z.record(z.string(), LeadSourceActionSchema).readonly().optional(),
    dateFormat: z.string().nullable().optional(),
    dryRun: z.boolean(),
  })
  .readonly();
export type ClientImportCommitRequest = z.output<typeof ClientImportCommitRequestSchema>;

export const ClientImportCommitResponseCreatedLeadSourceSchema = z
  .object({
    id: LeadSourceIdSchema,
    name: z.string(),
  })
  .readonly();
export type ClientImportCommitResponseCreatedLeadSource = z.output<typeof ClientImportCommitResponseCreatedLeadSourceSchema>;

export const ClientImportCommitResponseStatusSchema = z.enum(["OK", "ERROR"]);
export type ClientImportCommitResponseStatus = z.output<typeof ClientImportCommitResponseStatusSchema>;

export const ClientImportCommitResponseRowResultSchema = z
  .object({
    rowNumber: z.int32(),
    status: ClientImportCommitResponseStatusSchema,
    errors: z.array(z.string()).readonly(),
  })
  .readonly();
export type ClientImportCommitResponseRowResult = z.output<typeof ClientImportCommitResponseRowResultSchema>;

export const ClientImportCommitResponseSchema = z
  .object({
    totalRows: z.int32(),
    imported: z.int32(),
    skipped: z.int32(),
    rows: z.array(ClientImportCommitResponseRowResultSchema).readonly(),
    createdLeadSources: z.array(ClientImportCommitResponseCreatedLeadSourceSchema).readonly(),
  })
  .readonly();
export type ClientImportCommitResponse = z.output<typeof ClientImportCommitResponseSchema>;

export const ClientImportParseRequestSchema = z
  .object({
    uploadId: UploadIdSchema,
  })
  .readonly();
export type ClientImportParseRequest = z.output<typeof ClientImportParseRequestSchema>;

export const ClientImportParseResponseSchema = z
  .object({
    originalName: z.string(),
    totalRows: z.int32(),
    columns: z.array(z.string()).readonly(),
    sampleRows: z.array(z.array(z.string().nullable()).readonly()).readonly(),
    uniqueValuesPerColumn: z.record(z.string(), z.array(z.string()).readonly()).readonly(),
  })
  .readonly();
export type ClientImportParseResponse = z.output<typeof ClientImportParseResponseSchema>;

export const ClientListItemSchema = z
  .object({
    id: ClientIdSchema,
    name: z.string(),
    avatarId: UploadIdSchema.nullable(),
    birthday: LocalDateSchema.nullable(),
    gender: GenderSchema,
    groups: z.array(ClientGroupSchema).readonly(),
    balance: MoneySchema,
    customFields: z.array(CustomFieldValueSchema).readonly(),
    contacts: z.array(ClientContactSchemaSchema).readonly(),
    state: ClientStateSchema,
  })
  .readonly();
export type ClientListItem = z.output<typeof ClientListItemSchema>;

export const ClientSortFieldSchema = z.enum(["NAME", "BALANCE", "BIRTHDAY"]);
export type ClientSortField = z.output<typeof ClientSortFieldSchema>;

export const SortDirectionSchemaSchema = z.enum(["Asc", "Desc"]);
export type SortDirectionSchema = z.output<typeof SortDirectionSchemaSchema>;

export const DateRangeSchema = z
  .object({
    from: LocalDateSchema,
    to: LocalDateSchema,
  })
  .readonly();
export type DateRange = z.output<typeof DateRangeSchema>;

export const ClientListRequestSchema = z
  .object({
    name: z.string().nullable().optional(),
    archived: z.boolean().optional(),
    limit: z.int32().optional(),
    offset: z.int32().optional(),
    sortField: ClientSortFieldSchema.optional(),
    sortDirection: SortDirectionSchemaSchema.optional(),
    gender: GenderSchema.nullable().optional(),
    hasDebt: z.boolean().optional(),
    noGroup: z.boolean().optional(),
    groupId: GroupIdSchema.nullable().optional(),
    birthday: DateRangeSchema.nullable().optional(),
  })
  .readonly();
export type ClientListRequest = z.output<typeof ClientListRequestSchema>;

export const ClientListResponseSchema = z
  .object({
    clients: z.array(ClientListItemSchema).readonly(),
    total: z.int32(),
  })
  .readonly();
export type ClientListResponse = z.output<typeof ClientListResponseSchema>;

export const ClientNoteIdSchema = z.uuid().brand<"ClientNoteId">();
export type ClientNoteId = z.output<typeof ClientNoteIdSchema>;

export const ClientNoteSchemaSchema = z
  .object({
    id: ClientNoteIdSchema,
    text: z.string().max(2000),
    author: PerformedBySchema,
    createdAt: InstantSchema,
    updatedAt: InstantSchema.nullable(),
  })
  .readonly();
export type ClientNoteSchema = z.output<typeof ClientNoteSchemaSchema>;

export const ClientNotesListRequestSchema = z
  .object({
    clientId: ClientIdSchema,
  })
  .readonly();
export type ClientNotesListRequest = z.output<typeof ClientNotesListRequestSchema>;

export const ClientNotesListResponseSchema = z
  .object({
    notes: z.array(ClientNoteSchemaSchema).readonly(),
  })
  .readonly();
export type ClientNotesListResponse = z.output<typeof ClientNotesListResponseSchema>;

export const JsonValueSchema = z.json();
export type JsonValue = z.output<typeof JsonValueSchema>;

export const SortStateSchemaSchema = z
  .object({
    columnId: z.string(),
    direction: SortDirectionSchemaSchema,
  })
  .readonly();
export type SortStateSchema = z.output<typeof SortStateSchemaSchema>;

export const SavedViewSchemaSchema = z
  .object({
    id: z.string(),
    name: z.string(),
    filterJson: JsonValueSchema,
    sort: SortStateSchemaSchema.nullable(),
  })
  .readonly();
export type SavedViewSchema = z.output<typeof SavedViewSchemaSchema>;

export const ClientsDisplaySettingsSchema = z
  .object({
    columns: z.array(z.string()).readonly(),
    sort: SortStateSchemaSchema.nullable(),
    savedViews: z.array(SavedViewSchemaSchema).readonly(),
  })
  .readonly();
export type ClientsDisplaySettings = z.output<typeof ClientsDisplaySettingsSchema>;

export const SavedViewSchemaInputSchema = z
  .object({
    id: z.string(),
    name: z.string(),
    filterJson: JsonValueSchema,
    sort: SortStateSchemaSchema.nullable().optional(),
  })
  .readonly();
export type SavedViewSchemaInput = z.output<typeof SavedViewSchemaInputSchema>;

export const ClientsDisplaySettingsInputSchema = z
  .object({
    columns: z.array(z.string()).readonly().optional(),
    sort: SortStateSchemaSchema.nullable().optional(),
    savedViews: z.array(SavedViewSchemaInputSchema).readonly().optional(),
  })
  .readonly();
export type ClientsDisplaySettingsInput = z.output<typeof ClientsDisplaySettingsInputSchema>;

export const ConversationRequestSchema = z
  .object({
    clientId: ClientIdSchema,
  })
  .readonly();
export type ConversationRequest = z.output<typeof ConversationRequestSchema>;

export const MessageIdSchema = z.uuid().brand<"MessageId">();
export type MessageId = z.output<typeof MessageIdSchema>;

export const MessageSchemaInboundSchema = z
  .object({
    type: z.literal("inbound"),
    id: MessageIdSchema,
    body: z.string(),
    createdAt: z.string(),
    receivedVia: ChannelIntegrationIdSchema,
  })
  .readonly();
export type MessageSchemaInbound = z.output<typeof MessageSchemaInboundSchema>;

export const DeliveryStateSchemaSchema = z.enum(["PENDING", "SENT", "DELIVERED", "FAILED"]);
export type DeliveryStateSchema = z.output<typeof DeliveryStateSchemaSchema>;

export const DeliverySchemaSchema = z
  .object({
    channelIntegrationId: ChannelIntegrationIdSchema,
    state: DeliveryStateSchemaSchema,
    errorMessage: z.string().nullable(),
  })
  .readonly();
export type DeliverySchema = z.output<typeof DeliverySchemaSchema>;

export const MessageSchemaOutboundSchema = z
  .object({
    type: z.literal("outbound"),
    id: MessageIdSchema,
    body: z.string(),
    createdAt: z.string(),
    authorEmployeeId: EmployeeIdSchema.nullable(),
    deliveries: z.array(DeliverySchemaSchema).readonly(),
  })
  .readonly();
export type MessageSchemaOutbound = z.output<typeof MessageSchemaOutboundSchema>;

export const MessageSchemaSchema = z.discriminatedUnion("type", [MessageSchemaInboundSchema, MessageSchemaOutboundSchema]);
export type MessageSchema = z.output<typeof MessageSchemaSchema>;

export const ConversationResponseSchema = z
  .object({
    clientId: ClientIdSchema,
    messages: z.array(MessageSchemaSchema).readonly(),
    contacts: z.array(ClientContactSchemaSchema).readonly(),
  })
  .readonly();
export type ConversationResponse = z.output<typeof ConversationResponseSchema>;

export const CreateChannelIntegrationRequestSchema = z
  .object({
    id: ChannelIntegrationIdSchema,
    name: z.string(),
    config: ChannelConfigSchema,
  })
  .readonly();
export type CreateChannelIntegrationRequest = z.output<typeof CreateChannelIntegrationRequestSchema>;

export const CreateClientRequestSchema = z
  .object({
    id: ClientIdSchema,
    name: z.string(),
    avatarId: UploadIdSchema.nullable().optional(),
    birthday: LocalDateSchema.nullable().optional(),
    gender: GenderSchema,
    leadSourceId: LeadSourceIdSchema.nullable().optional(),
    customFields: z.array(CustomFieldValueSchema).readonly().optional(),
    contacts: z.array(ClientContactInputSchema).readonly().optional(),
  })
  .readonly();
export type CreateClientRequest = z.output<typeof CreateClientRequestSchema>;

export const DisciplineIdSchema = z.uuid().brand<"DisciplineId">();
export type DisciplineId = z.output<typeof DisciplineIdSchema>;

export const CreateDisciplineRequestSchema = z
  .object({
    id: DisciplineIdSchema,
    name: z.string(),
  })
  .readonly();
export type CreateDisciplineRequest = z.output<typeof CreateDisciplineRequestSchema>;

export const CreateEmployeeRequestSchema = z
  .object({
    id: EmployeeIdSchema,
    name: z.string(),
    phoneNo: z.string().nullable().optional(),
    email: z.string().nullable().optional(),
    avatarId: UploadIdSchema.nullable().optional(),
    roleIds: z.array(z.uuid()).readonly().optional(),
    grantedPermissions: z.array(UserPermissionSchema).readonly().optional(),
    revokedPermissions: z.array(UserPermissionSchema).readonly().optional(),
    allBranchesAccess: z.boolean().optional(),
    branchIds: z.array(BranchIdSchema).readonly().optional(),
  })
  .readonly();
export type CreateEmployeeRequest = z.output<typeof CreateEmployeeRequestSchema>;

export const HallIdSchema = z.uuid().brand<"HallId">();
export type HallId = z.output<typeof HallIdSchema>;

export const CreateHallRequestSchema = z
  .object({
    id: HallIdSchema,
    name: z.string(),
  })
  .readonly();
export type CreateHallRequest = z.output<typeof CreateHallRequestSchema>;

export const CreateLeadSourceRequestSchema = z
  .object({
    id: LeadSourceIdSchema,
    name: z.string(),
  })
  .readonly();
export type CreateLeadSourceRequest = z.output<typeof CreateLeadSourceRequestSchema>;

export const CreateRoleRequestSchema = z
  .object({
    id: z.uuid(),
    name: z.string(),
    permissions: z.array(UserPermissionSchema).readonly(),
  })
  .readonly();
export type CreateRoleRequest = z.output<typeof CreateRoleRequestSchema>;

export const LocalTimeSchema = z.iso.time().brand<"LocalTime">();
export type LocalTime = z.output<typeof LocalTimeSchema>;

export const SessionIdSchema = z.uuid().brand<"SessionId">();
export type SessionId = z.output<typeof SessionIdSchema>;

export const CreateSessionRequestSchema = z
  .object({
    id: SessionIdSchema,
    groupId: GroupIdSchema,
    date: LocalDateSchema,
    startTime: LocalTimeSchema,
    endTime: LocalTimeSchema,
    hallId: HallIdSchema,
    notes: z.string().nullable().optional(),
  })
  .readonly();
export type CreateSessionRequest = z.output<typeof CreateSessionRequestSchema>;

export const DurationUnitSchema = z.enum(["DAYS", "MONTHS"]);
export type DurationUnit = z.output<typeof DurationUnitSchema>;

export const CreateTariffPlanRequestSchema = z
  .object({
    id: TariffPlanIdSchema,
    name: z.string(),
    sessions: z.int32().nullable(),
    durationValue: z.int32(),
    durationUnit: DurationUnitSchema,
    price: MoneySchema,
  })
  .readonly();
export type CreateTariffPlanRequest = z.output<typeof CreateTariffPlanRequestSchema>;

export const CreateTaskRequestSchema = z
  .object({
    id: TaskIdSchema,
    title: z.string(),
    description: z.string(),
    clientId: ClientIdSchema.nullable(),
    dueDate: InstantSchema.nullable(),
    dueDateEnd: InstantSchema.nullable(),
    assigneeId: EmployeeIdSchema.nullable().optional(),
  })
  .readonly();
export type CreateTaskRequest = z.output<typeof CreateTaskRequestSchema>;

export const CustomFieldDefinitionBooleanSchema = z
  .object({
    fieldType: z.literal("boolean"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean(),
    isSearchable: z.boolean(),
    isSortable: z.boolean(),
  })
  .readonly();
export type CustomFieldDefinitionBoolean = z.output<typeof CustomFieldDefinitionBooleanSchema>;

export const CustomFieldDefinitionDateSchema = z
  .object({
    fieldType: z.literal("date"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean(),
    isSearchable: z.boolean(),
    isSortable: z.boolean(),
  })
  .readonly();
export type CustomFieldDefinitionDate = z.output<typeof CustomFieldDefinitionDateSchema>;

export const CustomFieldDefinitionEmailSchema = z
  .object({
    fieldType: z.literal("email"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean(),
    isSearchable: z.boolean(),
    isSortable: z.boolean(),
  })
  .readonly();
export type CustomFieldDefinitionEmail = z.output<typeof CustomFieldDefinitionEmailSchema>;

export const CustomFieldDefinitionNumberSchema = z
  .object({
    fieldType: z.literal("number"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean(),
    isSearchable: z.boolean(),
    isSortable: z.boolean(),
    minValue: z.int().nullable(),
    maxValue: z.int().nullable(),
  })
  .readonly();
export type CustomFieldDefinitionNumber = z.output<typeof CustomFieldDefinitionNumberSchema>;

export const CustomFieldDefinitionPhoneSchema = z
  .object({
    fieldType: z.literal("phone"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean(),
    isSearchable: z.boolean(),
    isSortable: z.boolean(),
  })
  .readonly();
export type CustomFieldDefinitionPhone = z.output<typeof CustomFieldDefinitionPhoneSchema>;

export const CustomFieldDefinitionSelectSchema = z
  .object({
    fieldType: z.literal("select"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean(),
    isSearchable: z.boolean(),
    isSortable: z.boolean(),
    options: z.array(z.string()).readonly(),
  })
  .readonly();
export type CustomFieldDefinitionSelect = z.output<typeof CustomFieldDefinitionSelectSchema>;

export const CustomFieldDefinitionTextSchema = z
  .object({
    fieldType: z.literal("text"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean(),
    isSearchable: z.boolean(),
    isSortable: z.boolean(),
    minLength: z.int32().nullable(),
    maxLength: z.int32().nullable(),
  })
  .readonly();
export type CustomFieldDefinitionText = z.output<typeof CustomFieldDefinitionTextSchema>;

export const CustomFieldDefinitionUrlSchema = z
  .object({
    fieldType: z.literal("url"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean(),
    isSearchable: z.boolean(),
    isSortable: z.boolean(),
  })
  .readonly();
export type CustomFieldDefinitionUrl = z.output<typeof CustomFieldDefinitionUrlSchema>;

export const CustomFieldDefinitionSchema = z.discriminatedUnion("fieldType", [CustomFieldDefinitionBooleanSchema, CustomFieldDefinitionDateSchema, CustomFieldDefinitionEmailSchema, CustomFieldDefinitionNumberSchema, CustomFieldDefinitionPhoneSchema, CustomFieldDefinitionSelectSchema, CustomFieldDefinitionTextSchema, CustomFieldDefinitionUrlSchema]);
export type CustomFieldDefinition = z.output<typeof CustomFieldDefinitionSchema>;

export const CustomFieldDefinitionBooleanInputSchema = z
  .object({
    fieldType: z.literal("boolean"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean().optional(),
    isSearchable: z.boolean().optional(),
    isSortable: z.boolean().optional(),
  })
  .readonly();
export type CustomFieldDefinitionBooleanInput = z.output<typeof CustomFieldDefinitionBooleanInputSchema>;

export const CustomFieldDefinitionDateInputSchema = z
  .object({
    fieldType: z.literal("date"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean().optional(),
    isSearchable: z.boolean().optional(),
    isSortable: z.boolean().optional(),
  })
  .readonly();
export type CustomFieldDefinitionDateInput = z.output<typeof CustomFieldDefinitionDateInputSchema>;

export const CustomFieldDefinitionEmailInputSchema = z
  .object({
    fieldType: z.literal("email"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean().optional(),
    isSearchable: z.boolean().optional(),
    isSortable: z.boolean().optional(),
  })
  .readonly();
export type CustomFieldDefinitionEmailInput = z.output<typeof CustomFieldDefinitionEmailInputSchema>;

export const CustomFieldDefinitionNumberInputSchema = z
  .object({
    fieldType: z.literal("number"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean().optional(),
    isSearchable: z.boolean().optional(),
    isSortable: z.boolean().optional(),
    minValue: z.int().nullable().optional(),
    maxValue: z.int().nullable().optional(),
  })
  .readonly();
export type CustomFieldDefinitionNumberInput = z.output<typeof CustomFieldDefinitionNumberInputSchema>;

export const CustomFieldDefinitionPhoneInputSchema = z
  .object({
    fieldType: z.literal("phone"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean().optional(),
    isSearchable: z.boolean().optional(),
    isSortable: z.boolean().optional(),
  })
  .readonly();
export type CustomFieldDefinitionPhoneInput = z.output<typeof CustomFieldDefinitionPhoneInputSchema>;

export const CustomFieldDefinitionSelectInputSchema = z
  .object({
    fieldType: z.literal("select"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean().optional(),
    isSearchable: z.boolean().optional(),
    isSortable: z.boolean().optional(),
    options: z.array(z.string()).readonly().optional(),
  })
  .readonly();
export type CustomFieldDefinitionSelectInput = z.output<typeof CustomFieldDefinitionSelectInputSchema>;

export const CustomFieldDefinitionTextInputSchema = z
  .object({
    fieldType: z.literal("text"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean().optional(),
    isSearchable: z.boolean().optional(),
    isSortable: z.boolean().optional(),
    minLength: z.int32().nullable().optional(),
    maxLength: z.int32().nullable().optional(),
  })
  .readonly();
export type CustomFieldDefinitionTextInput = z.output<typeof CustomFieldDefinitionTextInputSchema>;

export const CustomFieldDefinitionUrlInputSchema = z
  .object({
    fieldType: z.literal("url"),
    fieldKey: CustomFieldKeySchema,
    label: z.string(),
    isRequired: z.boolean().optional(),
    isSearchable: z.boolean().optional(),
    isSortable: z.boolean().optional(),
  })
  .readonly();
export type CustomFieldDefinitionUrlInput = z.output<typeof CustomFieldDefinitionUrlInputSchema>;

export const CustomFieldDefinitionInputSchema = z.discriminatedUnion("fieldType", [CustomFieldDefinitionBooleanInputSchema, CustomFieldDefinitionDateInputSchema, CustomFieldDefinitionEmailInputSchema, CustomFieldDefinitionNumberInputSchema, CustomFieldDefinitionPhoneInputSchema, CustomFieldDefinitionSelectInputSchema, CustomFieldDefinitionTextInputSchema, CustomFieldDefinitionUrlInputSchema]);
export type CustomFieldDefinitionInput = z.output<typeof CustomFieldDefinitionInputSchema>;

export const CustomFieldsListRequestSchema = z
  .object({
    entityType: z.string(),
  })
  .readonly();
export type CustomFieldsListRequest = z.output<typeof CustomFieldsListRequestSchema>;

export const DashboardWidgetIdSchema = z.uuid().brand<"DashboardWidgetId">();
export type DashboardWidgetId = z.output<typeof DashboardWidgetIdSchema>;

export const DashboardWidgetBirthdaysSchema = z
  .object({
    type: z.literal("birthdays"),
    id: DashboardWidgetIdSchema,
    title: z.string().nullable(),
    window: BirthdayWindowSchema,
    limit: z.int32(),
  })
  .readonly();
export type DashboardWidgetBirthdays = z.output<typeof DashboardWidgetBirthdaysSchema>;

export const DashboardWidgetDebtorsSchema = z
  .object({
    type: z.literal("debtors"),
    id: DashboardWidgetIdSchema,
    title: z.string().nullable(),
    limit: z.int32(),
  })
  .readonly();
export type DashboardWidgetDebtors = z.output<typeof DashboardWidgetDebtorsSchema>;

export const DashboardWidgetSessionsSchema = z
  .object({
    type: z.literal("sessions"),
    id: DashboardWidgetIdSchema,
    title: z.string().nullable(),
  })
  .readonly();
export type DashboardWidgetSessions = z.output<typeof DashboardWidgetSessionsSchema>;

export const DashboardWidgetSchema = z.discriminatedUnion("type", [DashboardWidgetBirthdaysSchema, DashboardWidgetDebtorsSchema, DashboardWidgetSessionsSchema]);
export type DashboardWidget = z.output<typeof DashboardWidgetSchema>;

export const DashboardSettingsSchema = z
  .object({
    widgets: z.array(DashboardWidgetSchema).readonly(),
    layout: z.array(DashboardWidgetIdSchema).readonly(),
  })
  .readonly();
export type DashboardSettings = z.output<typeof DashboardSettingsSchema>;

export const DashboardWidgetBirthdaysInputSchema = z
  .object({
    type: z.literal("birthdays"),
    id: DashboardWidgetIdSchema,
    title: z.string().nullable().optional(),
    window: BirthdayWindowSchema.optional(),
    limit: z.int32().optional(),
  })
  .readonly();
export type DashboardWidgetBirthdaysInput = z.output<typeof DashboardWidgetBirthdaysInputSchema>;

export const DashboardWidgetDebtorsInputSchema = z
  .object({
    type: z.literal("debtors"),
    id: DashboardWidgetIdSchema,
    title: z.string().nullable().optional(),
    limit: z.int32().optional(),
  })
  .readonly();
export type DashboardWidgetDebtorsInput = z.output<typeof DashboardWidgetDebtorsInputSchema>;

export const DashboardWidgetSessionsInputSchema = z
  .object({
    type: z.literal("sessions"),
    id: DashboardWidgetIdSchema,
    title: z.string().nullable().optional(),
  })
  .readonly();
export type DashboardWidgetSessionsInput = z.output<typeof DashboardWidgetSessionsInputSchema>;

export const DashboardWidgetInputSchema = z.discriminatedUnion("type", [DashboardWidgetBirthdaysInputSchema, DashboardWidgetDebtorsInputSchema, DashboardWidgetSessionsInputSchema]);
export type DashboardWidgetInput = z.output<typeof DashboardWidgetInputSchema>;

export const DashboardSettingsInputSchema = z
  .object({
    widgets: z.array(DashboardWidgetInputSchema).readonly().optional(),
    layout: z.array(DashboardWidgetIdSchema).readonly().optional(),
  })
  .readonly();
export type DashboardSettingsInput = z.output<typeof DashboardSettingsInputSchema>;

export const DayOfWeekSchema = z.enum(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]);
export type DayOfWeek = z.output<typeof DayOfWeekSchema>;

export const DeleteBranchRequestSchema = z
  .object({
    ids: z.array(BranchIdSchema).readonly(),
  })
  .readonly();
export type DeleteBranchRequest = z.output<typeof DeleteBranchRequestSchema>;

export const DeleteChannelIntegrationRequestSchema = z
  .object({
    id: ChannelIntegrationIdSchema,
  })
  .readonly();
export type DeleteChannelIntegrationRequest = z.output<typeof DeleteChannelIntegrationRequestSchema>;

export const DeleteClientDocRequestSchema = z
  .object({
    clientId: ClientIdSchema,
    docId: ClientDocIdSchema,
  })
  .readonly();
export type DeleteClientDocRequest = z.output<typeof DeleteClientDocRequestSchema>;

export const DeleteClientNoteRequestSchema = z
  .object({
    noteId: ClientNoteIdSchema,
  })
  .readonly();
export type DeleteClientNoteRequest = z.output<typeof DeleteClientNoteRequestSchema>;

export const DeleteDisciplineRequestSchema = z
  .object({
    ids: z.array(DisciplineIdSchema).readonly(),
  })
  .readonly();
export type DeleteDisciplineRequest = z.output<typeof DeleteDisciplineRequestSchema>;

export const DeleteHallRequestSchema = z
  .object({
    ids: z.array(HallIdSchema).readonly(),
  })
  .readonly();
export type DeleteHallRequest = z.output<typeof DeleteHallRequestSchema>;

export const DeleteLeadSourceRequestSchema = z
  .object({
    ids: z.array(LeadSourceIdSchema).readonly(),
  })
  .readonly();
export type DeleteLeadSourceRequest = z.output<typeof DeleteLeadSourceRequestSchema>;

export const DetachTaskUploadRequestSchema = z
  .object({
    taskId: TaskIdSchema,
    uploadId: UploadIdSchema,
  })
  .readonly();
export type DetachTaskUploadRequest = z.output<typeof DetachTaskUploadRequestSchema>;

export const DisciplineDetailResponseSchema = z
  .object({
    id: DisciplineIdSchema,
    name: z.string(),
  })
  .readonly();
export type DisciplineDetailResponse = z.output<typeof DisciplineDetailResponseSchema>;

export const DisciplineListResponseSchema = z
  .object({
    disciplines: z.array(DisciplineDetailResponseSchema).readonly(),
  })
  .readonly();
export type DisciplineListResponse = z.output<typeof DisciplineListResponseSchema>;

export const EmployeesDisplaySettingsSchema = z
  .object({
    columns: z.array(z.string()).readonly(),
    sort: SortStateSchemaSchema.nullable(),
    savedViews: z.array(SavedViewSchemaSchema).readonly(),
  })
  .readonly();
export type EmployeesDisplaySettings = z.output<typeof EmployeesDisplaySettingsSchema>;

export const GroupsDisplaySettingsSchema = z
  .object({
    columns: z.array(z.string()).readonly(),
    sort: SortStateSchemaSchema.nullable(),
    savedViews: z.array(SavedViewSchemaSchema).readonly(),
  })
  .readonly();
export type GroupsDisplaySettings = z.output<typeof GroupsDisplaySettingsSchema>;

export const TasksDisplaySettingsSchema = z
  .object({
    columns: z.array(z.string()).readonly(),
    sort: SortStateSchemaSchema.nullable(),
    savedViews: z.array(SavedViewSchemaSchema).readonly(),
  })
  .readonly();
export type TasksDisplaySettings = z.output<typeof TasksDisplaySettingsSchema>;

export const DisplaySettingsSchema = z
  .object({
    clients: ClientsDisplaySettingsSchema,
    groups: GroupsDisplaySettingsSchema,
    employees: EmployeesDisplaySettingsSchema,
    tasks: TasksDisplaySettingsSchema,
    dashboard: DashboardSettingsSchema,
  })
  .readonly();
export type DisplaySettings = z.output<typeof DisplaySettingsSchema>;

export const EmployeesDisplaySettingsInputSchema = z
  .object({
    columns: z.array(z.string()).readonly().optional(),
    sort: SortStateSchemaSchema.nullable().optional(),
    savedViews: z.array(SavedViewSchemaInputSchema).readonly().optional(),
  })
  .readonly();
export type EmployeesDisplaySettingsInput = z.output<typeof EmployeesDisplaySettingsInputSchema>;

export const GroupsDisplaySettingsInputSchema = z
  .object({
    columns: z.array(z.string()).readonly().optional(),
    sort: SortStateSchemaSchema.nullable().optional(),
    savedViews: z.array(SavedViewSchemaInputSchema).readonly().optional(),
  })
  .readonly();
export type GroupsDisplaySettingsInput = z.output<typeof GroupsDisplaySettingsInputSchema>;

export const TasksDisplaySettingsInputSchema = z
  .object({
    columns: z.array(z.string()).readonly().optional(),
    sort: SortStateSchemaSchema.nullable().optional(),
    savedViews: z.array(SavedViewSchemaInputSchema).readonly().optional(),
  })
  .readonly();
export type TasksDisplaySettingsInput = z.output<typeof TasksDisplaySettingsInputSchema>;

export const DisplaySettingsInputSchema = z
  .object({
    clients: ClientsDisplaySettingsInputSchema.optional(),
    groups: GroupsDisplaySettingsInputSchema.optional(),
    employees: EmployeesDisplaySettingsInputSchema.optional(),
    tasks: TasksDisplaySettingsInputSchema.optional(),
    dashboard: DashboardSettingsInputSchema.optional(),
  })
  .readonly();
export type DisplaySettingsInput = z.output<typeof DisplaySettingsInputSchema>;

export const EditClientNoteRequestSchema = z
  .object({
    noteId: ClientNoteIdSchema,
    text: z.string().max(2000),
  })
  .readonly();
export type EditClientNoteRequest = z.output<typeof EditClientNoteRequestSchema>;

export const EditClientRequestSchema = z
  .object({
    id: ClientIdSchema,
    name: z.string(),
    avatarId: UploadIdSchema.nullable().optional(),
    birthday: LocalDateSchema.nullable().optional(),
    gender: GenderSchema,
    leadSourceId: LeadSourceIdSchema.nullable().optional(),
    customFields: z.array(CustomFieldValueSchema).readonly().optional(),
    contacts: z.array(ClientContactInputSchema).readonly().optional(),
  })
  .readonly();
export type EditClientRequest = z.output<typeof EditClientRequestSchema>;

export const EditGroupRequestSchema = z
  .object({
    id: GroupIdSchema,
    name: z.string(),
    disciplineIds: z.array(DisciplineIdSchema).readonly().optional(),
    employeeIds: z.array(EmployeeIdSchema).readonly().optional(),
  })
  .readonly();
export type EditGroupRequest = z.output<typeof EditGroupRequestSchema>;

export const EmployeeDetailRequestSchema = z
  .object({
    id: EmployeeIdSchema,
  })
  .readonly();
export type EmployeeDetailRequest = z.output<typeof EmployeeDetailRequestSchema>;

export const EmployeeRoleSchema = z
  .object({
    id: z.uuid(),
    name: z.string(),
  })
  .readonly();
export type EmployeeRole = z.output<typeof EmployeeRoleSchema>;

export const EmployeeDetailResponseSchema = z
  .object({
    id: EmployeeIdSchema,
    name: z.string(),
    avatarId: UploadIdSchema.nullable(),
    isOwner: z.boolean(),
    isActive: z.boolean(),
    joinedAt: InstantSchema,
    roles: z.array(EmployeeRoleSchema).readonly(),
    phoneNo: z.string().nullable(),
    email: z.string().nullable(),
    grantedPermissions: z.array(UserPermissionSchema).readonly(),
    revokedPermissions: z.array(UserPermissionSchema).readonly(),
    allBranchesAccess: z.boolean(),
    branchIds: z.array(BranchIdSchema).readonly(),
  })
  .readonly();
export type EmployeeDetailResponse = z.output<typeof EmployeeDetailResponseSchema>;

export const EmployeeListItemSchema = z
  .object({
    id: EmployeeIdSchema,
    name: z.string(),
    avatarId: UploadIdSchema.nullable(),
    isOwner: z.boolean(),
    isActive: z.boolean(),
    joinedAt: InstantSchema,
    roles: z.array(EmployeeRoleSchema).readonly(),
    phoneNo: z.string().nullable(),
    email: z.string().nullable(),
  })
  .readonly();
export type EmployeeListItem = z.output<typeof EmployeeListItemSchema>;

export const EmployeeListResponseSchema = z
  .object({
    employees: z.array(EmployeeListItemSchema).readonly(),
    total: z.int32(),
  })
  .readonly();
export type EmployeeListResponse = z.output<typeof EmployeeListResponseSchema>;

export const FieldErrorSchema = z
  .object({
    name: z.string(),
    error: z.string(),
  })
  .readonly();
export type FieldError = z.output<typeof FieldErrorSchema>;

export const ErrorResponseSchema = z
  .object({
    code: z.string(),
    message: z.string(),
    fields: z.array(FieldErrorSchema).readonly().nullable(),
  })
  .readonly();
export type ErrorResponse = z.output<typeof ErrorResponseSchema>;

export const GroupClientSchema = z
  .object({
    id: ClientIdSchema,
    name: z.string(),
  })
  .readonly();
export type GroupClient = z.output<typeof GroupClientSchema>;

export const GroupCreateRequestSchema = z
  .object({
    id: GroupIdSchema,
    name: z.string(),
    disciplineIds: z.array(DisciplineIdSchema).readonly().optional(),
    employeeIds: z.array(EmployeeIdSchema).readonly().optional(),
  })
  .readonly();
export type GroupCreateRequest = z.output<typeof GroupCreateRequestSchema>;

export const GroupDetailRequestSchema = z
  .object({
    id: GroupIdSchema,
  })
  .readonly();
export type GroupDetailRequest = z.output<typeof GroupDetailRequestSchema>;

export const GroupDisciplineSchema = z
  .object({
    id: DisciplineIdSchema,
    name: z.string(),
  })
  .readonly();
export type GroupDiscipline = z.output<typeof GroupDisciplineSchema>;

export const GroupEmployeeSchema = z
  .object({
    id: EmployeeIdSchema,
    name: z.string(),
    avatarId: UploadIdSchema.nullable(),
  })
  .readonly();
export type GroupEmployee = z.output<typeof GroupEmployeeSchema>;

export const ValiditySchema = z
  .object({
    from: LocalDateSchema,
    to: LocalDateSchema.nullable(),
  })
  .readonly();
export type Validity = z.output<typeof ValiditySchema>;

export const ScheduleSlotSchema = z
  .object({
    dayOfWeek: DayOfWeekSchema,
    startAt: LocalTimeSchema,
    endAt: LocalTimeSchema,
    hallId: HallIdSchema,
    hallName: z.string().nullable(),
    validity: ValiditySchema.nullable(),
  })
  .readonly();
export type ScheduleSlot = z.output<typeof ScheduleSlotSchema>;

export const GroupDetailResponseSchema = z
  .object({
    id: GroupIdSchema,
    name: z.string(),
    schedule: z.array(ScheduleSlotSchema).readonly(),
    scheduleChangeAt: LocalDateSchema.nullable(),
    disciplines: z.array(GroupDisciplineSchema).readonly(),
    employees: z.array(GroupEmployeeSchema).readonly(),
    clients: z.array(GroupClientSchema).readonly(),
  })
  .readonly();
export type GroupDetailResponse = z.output<typeof GroupDetailResponseSchema>;

export const GroupListItemSchema = z
  .object({
    id: GroupIdSchema,
    name: z.string(),
    schedule: z.array(ScheduleSlotSchema).readonly(),
    scheduleChangeAt: LocalDateSchema.nullable(),
    employees: z.array(GroupEmployeeSchema).readonly(),
  })
  .readonly();
export type GroupListItem = z.output<typeof GroupListItemSchema>;

export const GroupListRequestSchema = z
  .object({
    name: z.string().nullable().optional(),
    disciplineIds: z.array(DisciplineIdSchema).readonly().optional(),
    employeeIds: z.array(EmployeeIdSchema).readonly().optional(),
  })
  .readonly();
export type GroupListRequest = z.output<typeof GroupListRequestSchema>;

export const GroupListResponseSchema = z
  .object({
    groups: z.array(GroupListItemSchema).readonly(),
    total: z.int32(),
  })
  .readonly();
export type GroupListResponse = z.output<typeof GroupListResponseSchema>;

export const GroupSelectItemSchema = z
  .object({
    id: GroupIdSchema,
    name: z.string(),
  })
  .readonly();
export type GroupSelectItem = z.output<typeof GroupSelectItemSchema>;

export const HallDetailResponseSchema = z
  .object({
    id: HallIdSchema,
    name: z.string(),
  })
  .readonly();
export type HallDetailResponse = z.output<typeof HallDetailResponseSchema>;

export const HallListResponseSchema = z
  .object({
    halls: z.array(HallDetailResponseSchema).readonly(),
  })
  .readonly();
export type HallListResponse = z.output<typeof HallListResponseSchema>;

export const InitiatePaymentRequestSchema = z
  .object({
    amount: z.int(),
    description: z.string(),
  })
  .readonly();
export type InitiatePaymentRequest = z.output<typeof InitiatePaymentRequestSchema>;

export const MembershipIdSchema = z.uuid().brand<"MembershipId">();
export type MembershipId = z.output<typeof MembershipIdSchema>;

export const IssueMembershipRequestSchema = z
  .object({
    id: MembershipIdSchema,
    clientId: ClientIdSchema,
    tariffPlanId: TariffPlanIdSchema.nullable(),
    name: z.string(),
    sessions: z.int32().nullable(),
    durationValue: z.int32(),
    durationUnit: DurationUnitSchema,
    startDate: LocalDateSchema,
    price: MoneySchema,
  })
  .readonly();
export type IssueMembershipRequest = z.output<typeof IssueMembershipRequestSchema>;

export const LeadSourceDetailResponseSchema = z
  .object({
    id: LeadSourceIdSchema,
    name: z.string(),
  })
  .readonly();
export type LeadSourceDetailResponse = z.output<typeof LeadSourceDetailResponseSchema>;

export const LeadSourceListResponseSchema = z
  .object({
    leadSources: z.array(LeadSourceDetailResponseSchema).readonly(),
  })
  .readonly();
export type LeadSourceListResponse = z.output<typeof LeadSourceListResponseSchema>;

export const LoginRequestSchema = z
  .object({
    username: z.string(),
    password: z.string(),
    branchId: BranchIdSchema,
  })
  .readonly();
export type LoginRequest = z.output<typeof LoginRequestSchema>;

export const LoginResponseSchema = z
  .object({
    accessToken: z.string(),
    refreshToken: z.string(),
  })
  .readonly();
export type LoginResponse = z.output<typeof LoginResponseSchema>;

export const MarkNotificationsReadRequestSchema = z
  .object({
    ids: z.array(z.uuid()).readonly(),
  })
  .readonly();
export type MarkNotificationsReadRequest = z.output<typeof MarkNotificationsReadRequestSchema>;

export const MembershipListRequestSchema = z
  .object({
    clientId: ClientIdSchema,
  })
  .readonly();
export type MembershipListRequest = z.output<typeof MembershipListRequestSchema>;

export const MembershipStatusSchema = z.enum(["ACTIVE", "EXPIRED"]);
export type MembershipStatus = z.output<typeof MembershipStatusSchema>;

export const MembershipSchemaSchema = z
  .object({
    id: MembershipIdSchema,
    name: z.string(),
    sessionsTotal: z.int32().nullable(),
    sessionsRemaining: z.int32().nullable(),
    startDate: LocalDateSchema,
    endDate: LocalDateSchema,
    price: MoneySchema,
    status: MembershipStatusSchema,
  })
  .readonly();
export type MembershipSchema = z.output<typeof MembershipSchemaSchema>;

export const MembershipListResponseSchema = z
  .object({
    memberships: z.array(MembershipSchemaSchema).readonly(),
  })
  .readonly();
export type MembershipListResponse = z.output<typeof MembershipListResponseSchema>;

export const NotificationItemSchema = z
  .object({
    id: z.uuid(),
    title: z.string(),
    body: z.string(),
    isRead: z.boolean(),
    createdAt: InstantSchema,
  })
  .readonly();
export type NotificationItem = z.output<typeof NotificationItemSchema>;

export const NotificationsRequestSchema = z
  .object({
    isRead: z.boolean().nullable().optional(),
  })
  .readonly();
export type NotificationsRequest = z.output<typeof NotificationsRequestSchema>;

export const NotificationsResponseSchema = z
  .object({
    notifications: z.array(NotificationItemSchema).readonly(),
    unreadCount: z.int32(),
  })
  .readonly();
export type NotificationsResponse = z.output<typeof NotificationsResponseSchema>;

export const OrgBalanceJournalEntrySchema = z
  .object({
    id: z.uuid(),
    amount: MoneySchema,
    balanceAfter: MoneySchema,
    operationType: z.string(),
    description: z.string(),
    createdAt: InstantSchema,
  })
  .readonly();
export type OrgBalanceJournalEntry = z.output<typeof OrgBalanceJournalEntrySchema>;

export const OrgBalanceDetailResponseSchema = z
  .object({
    totalAmount: MoneySchema,
    history: z.array(OrgBalanceJournalEntrySchema).readonly(),
  })
  .readonly();
export type OrgBalanceDetailResponse = z.output<typeof OrgBalanceDetailResponseSchema>;

export const OrgSettingsResponseSchema = z
  .object({
    name: z.string(),
    timezone: z.string(),
    currency: CurrencySchema,
  })
  .readonly();
export type OrgSettingsResponse = z.output<typeof OrgSettingsResponseSchema>;

export const PaymentUrlResponseSchema = z
  .object({
    paymentId: z.uuid(),
    confirmationUrl: z.string(),
  })
  .readonly();
export type PaymentUrlResponse = z.output<typeof PaymentUrlResponseSchema>;

export const RemoveClientFromGroupRequestSchema = z
  .object({
    clientIds: z.array(ClientIdSchema).readonly(),
    groupId: GroupIdSchema,
  })
  .readonly();
export type RemoveClientFromGroupRequest = z.output<typeof RemoveClientFromGroupRequestSchema>;

export const RescheduleSessionRequestSchema = z
  .object({
    newDate: LocalDateSchema,
    newStartTime: LocalTimeSchema,
    newEndTime: LocalTimeSchema,
    newHallId: HallIdSchema,
  })
  .readonly();
export type RescheduleSessionRequest = z.output<typeof RescheduleSessionRequestSchema>;

export const RestoreClientRequestSchema = z
  .object({
    clientIds: z.array(ClientIdSchema).readonly(),
  })
  .readonly();
export type RestoreClientRequest = z.output<typeof RestoreClientRequestSchema>;

export const RoleItemSchema = z
  .object({
    id: z.uuid(),
    name: z.string(),
    permissions: z.array(UserPermissionSchema).readonly(),
  })
  .readonly();
export type RoleItem = z.output<typeof RoleItemSchema>;

export const RoleListResponseSchema = z
  .object({
    roles: z.array(RoleItemSchema).readonly(),
  })
  .readonly();
export type RoleListResponse = z.output<typeof RoleListResponseSchema>;

export const SaveCustomFieldsRequestSchema = z
  .object({
    entityType: z.string(),
    fields: z.array(CustomFieldDefinitionInputSchema).readonly(),
  })
  .readonly();
export type SaveCustomFieldsRequest = z.output<typeof SaveCustomFieldsRequestSchema>;

export const ScheduleCoachSchemaSchema = z
  .object({
    id: EmployeeIdSchema,
    name: z.string(),
  })
  .readonly();
export type ScheduleCoachSchema = z.output<typeof ScheduleCoachSchemaSchema>;

export const ScheduleDisciplineSchemaSchema = z
  .object({
    id: DisciplineIdSchema,
    name: z.string(),
  })
  .readonly();
export type ScheduleDisciplineSchema = z.output<typeof ScheduleDisciplineSchemaSchema>;

export const ScheduleGroupSchemaSchema = z
  .object({
    id: GroupIdSchema,
    name: z.string(),
  })
  .readonly();
export type ScheduleGroupSchema = z.output<typeof ScheduleGroupSchemaSchema>;

export const ScheduleHallSchemaSchema = z
  .object({
    id: HallIdSchema,
    name: z.string(),
  })
  .readonly();
export type ScheduleHallSchema = z.output<typeof ScheduleHallSchemaSchema>;

export const ScheduleListRequestSchema = z
  .object({
    from: LocalDateSchema,
    to: LocalDateSchema,
    disciplineIds: z.array(DisciplineIdSchema).readonly().optional(),
    hallIds: z.array(HallIdSchema).readonly().optional(),
    employeeIds: z.array(EmployeeIdSchema).readonly().optional(),
  })
  .readonly();
export type ScheduleListRequest = z.output<typeof ScheduleListRequestSchema>;

export const SessionColorKeySchema = z.enum(["ORANGE", "PURPLE", "STEEL", "CYAN", "GREEN", "LIME", "LILAC", "GREY", "UNKNOWN"]);
export type SessionColorKey = z.output<typeof SessionColorKeySchema>;

export const SessionStatusSchema = z.enum(["SCHEDULED", "COMPLETED", "CANCELLED"]);
export type SessionStatus = z.output<typeof SessionStatusSchema>;

export const ScheduleSessionSchemaSchema = z
  .object({
    id: SessionIdSchema,
    group: ScheduleGroupSchemaSchema,
    date: LocalDateSchema,
    startTime: LocalTimeSchema,
    endTime: LocalTimeSchema,
    hall: ScheduleHallSchemaSchema,
    coaches: z.array(ScheduleCoachSchemaSchema).readonly(),
    disciplines: z.array(ScheduleDisciplineSchemaSchema).readonly(),
    status: SessionStatusSchema,
    colorKey: SessionColorKeySchema,
  })
  .readonly();
export type ScheduleSessionSchema = z.output<typeof ScheduleSessionSchemaSchema>;

export const ScheduleListResponseSchema = z
  .object({
    sessions: z.array(ScheduleSessionSchemaSchema).readonly(),
  })
  .readonly();
export type ScheduleListResponse = z.output<typeof ScheduleListResponseSchema>;

export const ValidityInputSchema = z
  .object({
    from: LocalDateSchema,
    to: LocalDateSchema.nullable().optional(),
  })
  .readonly();
export type ValidityInput = z.output<typeof ValidityInputSchema>;

export const ScheduleSlotInputSchema = z
  .object({
    dayOfWeek: DayOfWeekSchema,
    startAt: LocalTimeSchema,
    endAt: LocalTimeSchema,
    hallId: HallIdSchema,
    hallName: z.string().nullable().optional(),
    validity: ValidityInputSchema.nullable().optional(),
  })
  .readonly();
export type ScheduleSlotInput = z.output<typeof ScheduleSlotInputSchema>;

export const SendEmployeeAccessRequestSchema = z
  .object({
    employeeId: EmployeeIdSchema,
    email: z.string(),
    password: z.string(),
  })
  .readonly();
export type SendEmployeeAccessRequest = z.output<typeof SendEmployeeAccessRequestSchema>;

export const SendMessageRequestSchema = z
  .object({
    clientId: ClientIdSchema,
    channelIntegrationId: ChannelIntegrationIdSchema,
    body: z.string(),
    contactId: ClientContactIdSchema.nullable().optional(),
  })
  .readonly();
export type SendMessageRequest = z.output<typeof SendMessageRequestSchema>;

export const SessionDetailRequestSchema = z
  .object({
    id: SessionIdSchema,
  })
  .readonly();
export type SessionDetailRequest = z.output<typeof SessionDetailRequestSchema>;

export const SessionDetailResponseSchema = z
  .object({
    id: SessionIdSchema,
    groupId: GroupIdSchema,
    groupName: z.string(),
    date: LocalDateSchema,
    startTime: LocalTimeSchema,
    endTime: LocalTimeSchema,
    hallId: HallIdSchema,
    status: z.string(),
    isManual: z.boolean(),
    isRescheduled: z.boolean(),
    notes: z.string().nullable(),
    employeeIds: z.array(EmployeeIdSchema).readonly(),
    isEmployeeAssignmentOverridden: z.boolean(),
  })
  .readonly();
export type SessionDetailResponse = z.output<typeof SessionDetailResponseSchema>;

export const SetGroupDisciplinesRequestSchema = z
  .object({
    groupId: GroupIdSchema,
    disciplineIds: z.array(DisciplineIdSchema).readonly(),
  })
  .readonly();
export type SetGroupDisciplinesRequest = z.output<typeof SetGroupDisciplinesRequestSchema>;

export const SetGroupEmployeesRequestSchema = z
  .object({
    groupId: GroupIdSchema,
    employeeIds: z.array(EmployeeIdSchema).readonly(),
  })
  .readonly();
export type SetGroupEmployeesRequest = z.output<typeof SetGroupEmployeesRequestSchema>;

export const SetGroupScheduleRequestSchema = z
  .object({
    groupId: GroupIdSchema,
    effectiveFrom: LocalDateSchema.nullable().optional(),
    slots: z.array(ScheduleSlotInputSchema).readonly().optional(),
  })
  .readonly();
export type SetGroupScheduleRequest = z.output<typeof SetGroupScheduleRequestSchema>;

export const SetSessionEmployeesRequestSchema = z
  .object({
    sessionId: SessionIdSchema,
    employeeIds: z.array(EmployeeIdSchema).readonly(),
  })
  .readonly();
export type SetSessionEmployeesRequest = z.output<typeof SetSessionEmployeesRequestSchema>;

export const SignUpRequestSchema = z
  .object({
    companyName: z.string(),
    userName: z.string(),
    login: z.string(),
    password: z.string(),
    timezone: z.string(),
    currency: CurrencySchema,
  })
  .readonly();
export type SignUpRequest = z.output<typeof SignUpRequestSchema>;

export const SwitchBranchRequestSchema = z
  .object({
    branchId: BranchIdSchema,
  })
  .readonly();
export type SwitchBranchRequest = z.output<typeof SwitchBranchRequestSchema>;

export const TariffPlanListRequestSchema = z
  .object({
    includeArchived: z.boolean().optional(),
  })
  .readonly();
export type TariffPlanListRequest = z.output<typeof TariffPlanListRequestSchema>;

export const TariffPlanSchemaSchema = z
  .object({
    id: TariffPlanIdSchema,
    name: z.string(),
    sessions: z.int32().nullable(),
    durationValue: z.int32(),
    durationUnit: DurationUnitSchema,
    price: MoneySchema,
    archived: z.boolean(),
  })
  .readonly();
export type TariffPlanSchema = z.output<typeof TariffPlanSchemaSchema>;

export const TariffPlanListResponseSchema = z
  .object({
    tariffs: z.array(TariffPlanSchemaSchema).readonly(),
  })
  .readonly();
export type TariffPlanListResponse = z.output<typeof TariffPlanListResponseSchema>;

export const TaskDetailRequestSchema = z
  .object({
    taskId: TaskIdSchema,
  })
  .readonly();
export type TaskDetailRequest = z.output<typeof TaskDetailRequestSchema>;

export const UploadResponseSchema = z
  .object({
    id: UploadIdSchema,
    url: z.string(),
    originalName: z.string(),
    contentType: z.string(),
    sizeBytes: z.int(),
  })
  .readonly();
export type UploadResponse = z.output<typeof UploadResponseSchema>;

export const TaskStatusSchema = z.enum(["PENDING", "IN_PROGRESS", "PAUSED", "COMPLETED"]);
export type TaskStatus = z.output<typeof TaskStatusSchema>;

export const TaskDetailResponseSchema = z
  .object({
    id: TaskIdSchema,
    createdBy: EmployeeIdSchema,
    createdByName: z.string(),
    assigneeId: EmployeeIdSchema.nullable(),
    assigneeName: z.string().nullable(),
    clientId: ClientIdSchema.nullable(),
    clientName: z.string().nullable(),
    title: z.string(),
    description: z.string(),
    status: TaskStatusSchema,
    dueDate: InstantSchema.nullable(),
    dueDateEnd: InstantSchema.nullable(),
    completedAt: InstantSchema.nullable(),
    createdAt: InstantSchema,
    attachments: z.array(UploadResponseSchema).readonly(),
  })
  .readonly();
export type TaskDetailResponse = z.output<typeof TaskDetailResponseSchema>;

export const TaskListItemSchemaSchema = z
  .object({
    id: TaskIdSchema,
    title: z.string(),
    assigneeId: EmployeeIdSchema.nullable(),
    assigneeName: z.string().nullable(),
    clientId: ClientIdSchema.nullable(),
    clientName: z.string().nullable(),
    status: TaskStatusSchema,
    dueDate: InstantSchema.nullable(),
    dueDateEnd: InstantSchema.nullable(),
  })
  .readonly();
export type TaskListItemSchema = z.output<typeof TaskListItemSchemaSchema>;

export const TaskListRequestSchema = z
  .object({
    onlyMine: z.boolean().optional(),
    statuses: z.array(TaskStatusSchema).readonly().optional(),
    dueDateFrom: InstantSchema.nullable().optional(),
    dueDateTo: InstantSchema.nullable().optional(),
    clientId: ClientIdSchema.nullable().optional(),
    searchText: z.string().nullable().optional(),
    limit: z.int32().optional(),
    offset: z.int32().optional(),
  })
  .readonly();
export type TaskListRequest = z.output<typeof TaskListRequestSchema>;

export const TaskListResponseSchema = z
  .object({
    tasks: z.array(TaskListItemSchemaSchema).readonly(),
    total: z.int32(),
  })
  .readonly();
export type TaskListResponse = z.output<typeof TaskListResponseSchema>;

export const TodaySessionItemSchema = z
  .object({
    sessionId: SessionIdSchema,
    groupName: z.string(),
    startTime: LocalTimeSchema,
    endTime: LocalTimeSchema,
    hallName: z.string(),
  })
  .readonly();
export type TodaySessionItem = z.output<typeof TodaySessionItemSchema>;

export const TodaySessionsResponseSchema = z
  .object({
    date: LocalDateSchema,
    sessions: z.array(TodaySessionItemSchema).readonly(),
  })
  .readonly();
export type TodaySessionsResponse = z.output<typeof TodaySessionsResponseSchema>;

export const UnassignTaskRequestSchema = z
  .object({
    taskIds: z.array(TaskIdSchema).readonly(),
  })
  .readonly();
export type UnassignTaskRequest = z.output<typeof UnassignTaskRequestSchema>;

export const UpdateChannelIntegrationRequestSchema = z
  .object({
    id: ChannelIntegrationIdSchema,
    name: z.string(),
    config: ChannelConfigSchema,
    enabled: z.boolean(),
  })
  .readonly();
export type UpdateChannelIntegrationRequest = z.output<typeof UpdateChannelIntegrationRequestSchema>;

export const UpdateDisciplineRequestSchema = z
  .object({
    id: DisciplineIdSchema,
    name: z.string(),
  })
  .readonly();
export type UpdateDisciplineRequest = z.output<typeof UpdateDisciplineRequestSchema>;

export const UpdateEmployeeRequestSchema = z
  .object({
    id: EmployeeIdSchema,
    name: z.string(),
    phoneNo: z.string().nullable().optional(),
    email: z.string().nullable().optional(),
    avatarId: UploadIdSchema.nullable().optional(),
    roleIds: z.array(z.uuid()).readonly().optional(),
    grantedPermissions: z.array(UserPermissionSchema).readonly().optional(),
    revokedPermissions: z.array(UserPermissionSchema).readonly().optional(),
    allBranchesAccess: z.boolean().optional(),
    branchIds: z.array(BranchIdSchema).readonly().optional(),
  })
  .readonly();
export type UpdateEmployeeRequest = z.output<typeof UpdateEmployeeRequestSchema>;

export const UpdateHallRequestSchema = z
  .object({
    id: HallIdSchema,
    name: z.string(),
  })
  .readonly();
export type UpdateHallRequest = z.output<typeof UpdateHallRequestSchema>;

export const UpdateLeadSourceRequestSchema = z
  .object({
    id: LeadSourceIdSchema,
    name: z.string(),
  })
  .readonly();
export type UpdateLeadSourceRequest = z.output<typeof UpdateLeadSourceRequestSchema>;

export const UpdateMeRequestSchema = z
  .object({
    name: z.string(),
    avatarId: UploadIdSchema.nullable().optional(),
  })
  .readonly();
export type UpdateMeRequest = z.output<typeof UpdateMeRequestSchema>;

export const UpdateOrgSettingsRequestSchema = z
  .object({
    name: z.string(),
    timezone: z.string(),
  })
  .readonly();
export type UpdateOrgSettingsRequest = z.output<typeof UpdateOrgSettingsRequestSchema>;

export const UpdateRoleRequestSchema = z
  .object({
    id: z.uuid(),
    name: z.string(),
    permissions: z.array(UserPermissionSchema).readonly(),
  })
  .readonly();
export type UpdateRoleRequest = z.output<typeof UpdateRoleRequestSchema>;

export const UpdateTariffPlanRequestSchema = z
  .object({
    id: TariffPlanIdSchema,
    name: z.string(),
    sessions: z.int32().nullable(),
    durationValue: z.int32(),
    durationUnit: DurationUnitSchema,
    price: MoneySchema,
  })
  .readonly();
export type UpdateTariffPlanRequest = z.output<typeof UpdateTariffPlanRequestSchema>;

export const UpdateTaskRequestSchema = z
  .object({
    id: TaskIdSchema,
    title: z.string(),
    description: z.string(),
    clientId: ClientIdSchema.nullable(),
    dueDate: InstantSchema.nullable(),
    dueDateEnd: InstantSchema.nullable(),
  })
  .readonly();
export type UpdateTaskRequest = z.output<typeof UpdateTaskRequestSchema>;

export const UpdateTaskStatusRequestSchema = z
  .object({
    taskIds: z.array(TaskIdSchema).readonly(),
    status: TaskStatusSchema,
  })
  .readonly();
export type UpdateTaskStatusRequest = z.output<typeof UpdateTaskStatusRequestSchema>;

export const UploadInfoRequestSchema = z
  .object({
    id: UploadIdSchema,
  })
  .readonly();
export type UploadInfoRequest = z.output<typeof UploadInfoRequestSchema>;

/** Все эндпоинты API: путь без `/api/` → метод, формат запроса и ответа. */
export const endpoints = {
  "audit/log": {
    method: "GET",
    in: "query",
    request: AuditLogListRequestSchema,
    out: "json",
    response: AuditLogListResponseSchema,
  },
  "auth/branches": {
    method: "POST",
    in: "body",
    request: AuthBranchesRequestSchema,
    out: "json",
    response: AuthBranchesResponseSchema,
  },
  "auth/login": {
    method: "POST",
    in: "body",
    request: LoginRequestSchema,
    out: "json",
    response: LoginResponseSchema,
  },
  "auth/logout": {
    method: "POST",
    in: "none",
    request: z.undefined(),
    out: "empty",
    response: z.undefined(),
  },
  "auth/me": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: AuthMeResponseSchema,
  },
  "auth/me/change-password": {
    method: "POST",
    in: "body",
    request: ChangePasswordRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "auth/me/update": {
    method: "POST",
    in: "body",
    request: UpdateMeRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "auth/my-branches": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: AuthBranchesResponseSchema,
  },
  "auth/refresh-token": {
    method: "POST",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: LoginResponseSchema,
  },
  "auth/sign-up": {
    method: "POST",
    in: "body",
    request: SignUpRequestSchema,
    out: "json",
    response: LoginResponseSchema,
  },
  "auth/switch-branch": {
    method: "POST",
    in: "body",
    request: SwitchBranchRequestSchema,
    out: "json",
    response: LoginResponseSchema,
  },
  "branches/create": {
    method: "POST",
    in: "body",
    request: BranchCreateRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "branches/delete": {
    method: "POST",
    in: "body",
    request: DeleteBranchRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "branches/list": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: BranchListResponseSchema,
  },
  "branches/update": {
    method: "POST",
    in: "body",
    request: BranchUpdateRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "channels/create": {
    method: "POST",
    in: "body",
    request: CreateChannelIntegrationRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "channels/delete": {
    method: "POST",
    in: "body",
    request: DeleteChannelIntegrationRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "channels/list": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: ChannelListResponseSchema,
  },
  "channels/update": {
    method: "POST",
    in: "body",
    request: UpdateChannelIntegrationRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "clients/add-to-group": {
    method: "POST",
    in: "body",
    request: AddClientsToGroupRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "clients/archive": {
    method: "POST",
    in: "body",
    request: ArchiveClientRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "clients/balance/adjust": {
    method: "POST",
    in: "body",
    request: AdjustBalanceRequestSchema,
    out: "json",
    response: ClientDetailResponseSchema,
  },
  "clients/balance/history": {
    method: "GET",
    in: "query",
    request: ClientBalanceHistoryRequestSchema,
    out: "json",
    response: ClientBalanceHistoryResponseSchema,
  },
  "clients/create": {
    method: "POST",
    in: "body",
    request: CreateClientRequestSchema,
    out: "json",
    response: ClientDetailResponseSchema,
  },
  "clients/detail": {
    method: "GET",
    in: "query",
    request: ClientDetailRequestSchema,
    out: "json",
    response: ClientDetailResponseSchema,
  },
  "clients/docs/attach": {
    method: "POST",
    in: "body",
    request: AttachClientDocRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "clients/docs/delete": {
    method: "POST",
    in: "body",
    request: DeleteClientDocRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "clients/edit": {
    method: "POST",
    in: "body",
    request: EditClientRequestSchema,
    out: "json",
    response: ClientDetailResponseSchema,
  },
  "clients/export": {
    method: "POST",
    in: "body",
    request: ClientExportRequestSchema,
    out: "file",
    response: z.instanceof(Blob),
  },
  "clients/import/commit": {
    method: "POST",
    in: "body",
    request: ClientImportCommitRequestSchema,
    out: "json",
    response: ClientImportCommitResponseSchema,
  },
  "clients/import/parse": {
    method: "POST",
    in: "body",
    request: ClientImportParseRequestSchema,
    out: "json",
    response: ClientImportParseResponseSchema,
  },
  "clients/list": {
    method: "POST",
    in: "body",
    request: ClientListRequestSchema,
    out: "json",
    response: ClientListResponseSchema,
  },
  "clients/notes/add": {
    method: "POST",
    in: "body",
    request: AddClientNoteRequestSchema,
    out: "json",
    response: ClientNotesListResponseSchema,
  },
  "clients/notes/delete": {
    method: "POST",
    in: "body",
    request: DeleteClientNoteRequestSchema,
    out: "json",
    response: ClientNotesListResponseSchema,
  },
  "clients/notes/edit": {
    method: "POST",
    in: "body",
    request: EditClientNoteRequestSchema,
    out: "json",
    response: ClientNotesListResponseSchema,
  },
  "clients/notes/list": {
    method: "GET",
    in: "query",
    request: ClientNotesListRequestSchema,
    out: "json",
    response: ClientNotesListResponseSchema,
  },
  "clients/remove-from-group": {
    method: "POST",
    in: "body",
    request: RemoveClientFromGroupRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "clients/restore": {
    method: "POST",
    in: "body",
    request: RestoreClientRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "custom-fields/list": {
    method: "GET",
    in: "query",
    request: CustomFieldsListRequestSchema,
    out: "json",
    response: z.array(CustomFieldDefinitionSchema).readonly(),
  },
  "custom-fields/save": {
    method: "POST",
    in: "body",
    request: SaveCustomFieldsRequestSchema,
    out: "json",
    response: z.array(CustomFieldDefinitionSchema).readonly(),
  },
  "disciplines/create": {
    method: "POST",
    in: "body",
    request: CreateDisciplineRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "disciplines/delete": {
    method: "POST",
    in: "body",
    request: DeleteDisciplineRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "disciplines/list": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: DisciplineListResponseSchema,
  },
  "disciplines/update": {
    method: "POST",
    in: "body",
    request: UpdateDisciplineRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "display-settings": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: DisplaySettingsSchema,
  },
  "display-settings/update": {
    method: "POST",
    in: "body",
    request: DisplaySettingsInputSchema,
    out: "json",
    response: DisplaySettingsSchema,
  },
  "employees/create": {
    method: "POST",
    in: "body",
    request: CreateEmployeeRequestSchema,
    out: "json",
    response: EmployeeListItemSchema,
  },
  "employees/detail": {
    method: "GET",
    in: "query",
    request: EmployeeDetailRequestSchema,
    out: "json",
    response: EmployeeDetailResponseSchema,
  },
  "employees/list": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: EmployeeListResponseSchema,
  },
  "employees/roles": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: RoleListResponseSchema,
  },
  "employees/roles/create": {
    method: "POST",
    in: "body",
    request: CreateRoleRequestSchema,
    out: "json",
    response: RoleItemSchema,
  },
  "employees/roles/update": {
    method: "POST",
    in: "body",
    request: UpdateRoleRequestSchema,
    out: "json",
    response: RoleItemSchema,
  },
  "employees/send-access": {
    method: "POST",
    in: "body",
    request: SendEmployeeAccessRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "employees/update": {
    method: "POST",
    in: "body",
    request: UpdateEmployeeRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "groups/create": {
    method: "POST",
    in: "body",
    request: GroupCreateRequestSchema,
    out: "json",
    response: GroupDetailResponseSchema,
  },
  "groups/detail": {
    method: "GET",
    in: "query",
    request: GroupDetailRequestSchema,
    out: "json",
    response: GroupDetailResponseSchema,
  },
  "groups/edit": {
    method: "POST",
    in: "body",
    request: EditGroupRequestSchema,
    out: "json",
    response: GroupDetailResponseSchema,
  },
  "groups/list": {
    method: "POST",
    in: "body",
    request: GroupListRequestSchema,
    out: "json",
    response: GroupListResponseSchema,
  },
  "groups/list-for-select": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: z.array(GroupSelectItemSchema).readonly(),
  },
  "groups/set-disciplines": {
    method: "POST",
    in: "body",
    request: SetGroupDisciplinesRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "groups/set-employees": {
    method: "POST",
    in: "body",
    request: SetGroupEmployeesRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "groups/set-schedule": {
    method: "POST",
    in: "body",
    request: SetGroupScheduleRequestSchema,
    out: "json",
    response: GroupDetailResponseSchema,
  },
  "halls/create": {
    method: "POST",
    in: "body",
    request: CreateHallRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "halls/delete": {
    method: "POST",
    in: "body",
    request: DeleteHallRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "halls/list": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: HallListResponseSchema,
  },
  "halls/update": {
    method: "POST",
    in: "body",
    request: UpdateHallRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "home/today-sessions": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: TodaySessionsResponseSchema,
  },
  "lead-sources/create": {
    method: "POST",
    in: "body",
    request: CreateLeadSourceRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "lead-sources/delete": {
    method: "POST",
    in: "body",
    request: DeleteLeadSourceRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "lead-sources/list": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: LeadSourceListResponseSchema,
  },
  "lead-sources/update": {
    method: "POST",
    in: "body",
    request: UpdateLeadSourceRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "memberships/issue": {
    method: "POST",
    in: "body",
    request: IssueMembershipRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "memberships/list": {
    method: "POST",
    in: "body",
    request: MembershipListRequestSchema,
    out: "json",
    response: MembershipListResponseSchema,
  },
  "messaging/conversation": {
    method: "GET",
    in: "query",
    request: ConversationRequestSchema,
    out: "json",
    response: ConversationResponseSchema,
  },
  "messaging/send": {
    method: "POST",
    in: "body",
    request: SendMessageRequestSchema,
    out: "json",
    response: ConversationResponseSchema,
  },
  "notifications": {
    method: "GET",
    in: "query",
    request: NotificationsRequestSchema,
    out: "json",
    response: NotificationsResponseSchema,
  },
  "notifications/mark-all-read": {
    method: "POST",
    in: "none",
    request: z.undefined(),
    out: "empty",
    response: z.undefined(),
  },
  "notifications/mark-as-read": {
    method: "POST",
    in: "body",
    request: MarkNotificationsReadRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "org-balance/detail": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: OrgBalanceDetailResponseSchema,
  },
  "org/settings": {
    method: "GET",
    in: "none",
    request: z.undefined(),
    out: "json",
    response: OrgSettingsResponseSchema,
  },
  "org/settings/update": {
    method: "POST",
    in: "body",
    request: UpdateOrgSettingsRequestSchema,
    out: "json",
    response: OrgSettingsResponseSchema,
  },
  "payments/initiate": {
    method: "POST",
    in: "body",
    request: InitiatePaymentRequestSchema,
    out: "json",
    response: PaymentUrlResponseSchema,
  },
  "schedule/list": {
    method: "POST",
    in: "body",
    request: ScheduleListRequestSchema,
    out: "json",
    response: ScheduleListResponseSchema,
  },
  "sessions/create": {
    method: "POST",
    in: "body",
    request: CreateSessionRequestSchema,
    out: "json",
    response: SessionDetailResponseSchema,
  },
  "sessions/detail": {
    method: "GET",
    in: "query",
    request: SessionDetailRequestSchema,
    out: "json",
    response: SessionDetailResponseSchema,
  },
  "sessions/set-employees": {
    method: "POST",
    in: "body",
    request: SetSessionEmployeesRequestSchema,
    out: "json",
    response: SessionDetailResponseSchema,
  },
  "sessions/{id}/cancel": {
    method: "POST",
    in: "none",
    request: z.undefined(),
    out: "empty",
    response: z.undefined(),
  },
  "sessions/{id}/reschedule": {
    method: "POST",
    in: "body",
    request: RescheduleSessionRequestSchema,
    out: "json",
    response: SessionDetailResponseSchema,
  },
  "tariffs/archive": {
    method: "POST",
    in: "body",
    request: ArchiveTariffPlanRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "tariffs/create": {
    method: "POST",
    in: "body",
    request: CreateTariffPlanRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "tariffs/list": {
    method: "POST",
    in: "body",
    request: TariffPlanListRequestSchema,
    out: "json",
    response: TariffPlanListResponseSchema,
  },
  "tariffs/update": {
    method: "POST",
    in: "body",
    request: UpdateTariffPlanRequestSchema,
    out: "empty",
    response: z.undefined(),
  },
  "tasks/assign": {
    method: "POST",
    in: "body",
    request: AssignTaskRequestSchema,
    out: "json",
    response: BulkUpdateTasksResponseSchema,
  },
  "tasks/attach": {
    method: "POST",
    in: "body",
    request: AttachTaskUploadRequestSchema,
    out: "json",
    response: TaskDetailResponseSchema,
  },
  "tasks/create": {
    method: "POST",
    in: "body",
    request: CreateTaskRequestSchema,
    out: "json",
    response: TaskDetailResponseSchema,
  },
  "tasks/detach": {
    method: "POST",
    in: "body",
    request: DetachTaskUploadRequestSchema,
    out: "json",
    response: TaskDetailResponseSchema,
  },
  "tasks/detail": {
    method: "POST",
    in: "body",
    request: TaskDetailRequestSchema,
    out: "json",
    response: TaskDetailResponseSchema,
  },
  "tasks/list": {
    method: "POST",
    in: "body",
    request: TaskListRequestSchema,
    out: "json",
    response: TaskListResponseSchema,
  },
  "tasks/status": {
    method: "POST",
    in: "body",
    request: UpdateTaskStatusRequestSchema,
    out: "json",
    response: BulkUpdateTasksResponseSchema,
  },
  "tasks/unassign": {
    method: "POST",
    in: "body",
    request: UnassignTaskRequestSchema,
    out: "json",
    response: BulkUpdateTasksResponseSchema,
  },
  "tasks/update": {
    method: "POST",
    in: "body",
    request: UpdateTaskRequestSchema,
    out: "json",
    response: TaskDetailResponseSchema,
  },
  "upload": {
    method: "POST",
    in: "multipart",
    request: z.instanceof(FormData),
    out: "json",
    response: UploadResponseSchema,
  },
  "upload/info": {
    method: "GET",
    in: "query",
    request: UploadInfoRequestSchema,
    out: "json",
    response: UploadResponseSchema,
  },
} as const;
