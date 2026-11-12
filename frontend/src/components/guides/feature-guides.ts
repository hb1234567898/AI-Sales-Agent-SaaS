export interface GuideStep {
  title: string
  target: string
  description: string
  result: string
  allowInteraction?: boolean
  reveal?: 'knowledge-documents' | 'knowledge-ask' | 'customer-profile' | 'customer-interactions' | 'token-budget' | 'token-usage'
}
export interface FeatureGuide {
  id: string
  title: string
  heading: string
  path: string
  group: '销售工作' | 'AI 与协作' | '配置与管理'
  summary: string
  prerequisite: string
  guestNote: string
  adminOnly?: boolean
  steps: GuideStep[]
}

export const featureGuides: FeatureGuide[] = [
  {
    id: 'products', title: '产品目录与价格表', heading: '产品与价格', path: '/app/products', group: '销售工作',
    summary: '维护产品标准价及有效期内的合作价格，为报价提供统一定价来源。', prerequisite: '所有者、管理员或主管可以维护；报价使用已启用产品。', guestNote: '可以查看模拟产品与价格表，修改和保存需登录并具备管理权限。',
    steps: [
      { title: '产品与价格表分开管理', target: '.commercial-tabs', description: '产品目录保存编码、计价单位、未税标准价和税率。价格表保存特定合作价与生效期间；同一产品可出现在不同价格表。', result: '区分产品基本资料和特定的价格政策。', allowInteraction: true },
      { title: '核对报价基础', target: '[data-guide="commercial-catalog"] .commercial-table-wrap', description: '报价选择产品后由服务器读取单价和税率。使用指定价格表时，明细产品必须在该价格表中，且价格表已启用并处于有效期。', result: '避免使用未生效或已停用的价格。' },
      { title: '维护价格而不覆盖历史报价', target: '.commercial-heading', description: '有权限的成员可新建产品、修改标准价或维护价格表。价格变更只影响新报价版本，已保存的报价明细仍保留原单价和税率。', result: '价格可以更新，已发给客户的报价仍可追溯。' },
    ],
  },
  {
    id: 'quotes', title: '报价、版本与客户确认', heading: '报价单', path: '/app/quotes', group: '销售工作',
    summary: '从商机创建报价，核算金额后定稿，保留客户确认和历史版本。', prerequisite: '先有进行中的商机和启用的产品；销售只能操作分配给自己的商机。', guestNote: '可查看模拟报价、切换历史版本；创建、导出与邮件发送需正式账号。',
    steps: [
      { title: '识别待推进的报价', target: '[data-guide="commercial-quote-metrics"]', description: '草稿报价尚未定稿，待客户确认是已定稿报价。已签约和未收款金额来自合同与回款记录，不代表报价金额已到账。', result: '区分报价、签约和实际回款。' },
      { title: '搜索并核对状态', target: '[data-guide="commercial-quote-toolbar"]', description: '按客户、单号或报价名称搜索，按草稿、定稿、确认、拒绝或取消筛选。点击报价名称查看明细和操作记录。', result: '定位准确的客户报价。', allowInteraction: true },
      { title: '创建与保存版本', target: '.commercial-heading', description: '选择商机、价格来源和产品，填写数量、折扣、有效期与条款。点击计算报价核对服务端金额，再保存草稿。修改现有报价会新建版本而非覆盖历史。', result: '形成含单价、税率、折扣和总额的独立快照。' },
      { title: '定稿、发送和确认', target: '[data-guide="commercial-quotes"] .commercial-table-wrap', description: '主管可定稿报价，销售可提交带版本附件的邮件审批；批准后才会发送，定稿不代表已经发送。客户确认需记录依据，确认后可生成合同。历史版本可查看和导出 CSV。', result: '报价的财务口径、发送状态和客户结论都有记录。' },
    ],
  },
  {
    id: 'contracts', title: '合同签约与分期回款', heading: '合同与回款', path: '/app/contracts', group: '销售工作',
    summary: '把客户确认的报价转为合同，登记签约，再按分期核对收款。', prerequisite: '创建合同需客户确认报价；签约和回款需所有者、管理员或主管权限。', guestNote: '可查看模拟合同、分期和流水，不会产生真实合同或收款。',
    steps: [
      { title: '核对签约与应收', target: '[data-guide="commercial-contract-metrics"]', description: '已签约包含已签约和已结清合同。未收款只统计已签约合同，逾期按上海时区到期日判断；草稿不进入应收。', result: '了解真实应收，不把草稿算作收入。' },
      { title: '切换合同与应收', target: '.commercial-tabs', description: '合同管理展示合同与付款进度；应收回款展示未结清分期，可只看逾期，并进入对应合同登记回款。', result: '从合同维度或到期款项维度推进工作。', allowInteraction: true },
      { title: '绑定确认版本', target: '.commercial-heading', description: '新建合同选择客户已确认的报价。合同金额锁定为该版本含税总额，填写服务期间、条款及分期计划，各期金额合计必须等于合同总额。', result: '报价和合同金额保持一致。' },
      { title: '签约后再登记回款', target: '[data-guide="commercial-contracts"] .commercial-table-wrap', description: '打开合同，选择客户已有签署附件或本地文件后确认签约。本地文件只在确认时上传。签约会将关联商机标记为赢单。每次回款需填到账日和唯一流水号，不能超收，结清后自动关闭应收。', result: '从签署文件、回款计划到流水形成完整记录。' },
    ],
  },
  {
    id: 'today', title: '今日工作台', heading: '今日工作台', path: '/app/today', group: '销售工作',
    summary: '先判断今天要推进哪些客户，再进入跟进或审批处理具体事项。',
    prerequisite: '可直接浏览；当前工作台指标与队列展示演示内容。', guestNote: '可查看全部示例，并通过链接进入其他功能。',
    steps: [
      { title: '先看今天的工作量', target: '.metric-strip', description: '从待跟进、高优先级客户、待审批和触达量判断当天的重点。结合每项指标旁的说明理解统计含义。', result: '知道应该优先看客户跟进，还是先处理人工审批。' },
      { title: '从优先队列找客户', target: '.priority-panel', description: '对照客户评分、最近互动、建议动作和负责人，选择值得推进的客户。点击行末“处理”进入跟进任务。', result: '把客户信号转成可执行的下一步工作。' },
      { title: '检查需要确认的动作', target: '.review-panel', description: '查看 AI 建议的客户和动作摘要，点击“审核”进入审批中心。发送邮件等外部动作需要先核对内容再批准。', result: '明确哪些事项需要人工决策。' },
      { title: '追踪最近执行情况', target: '.runs-panel', description: '对照运行名称、处理范围和结果状态，点击“运行记录”进入 Agent 运行页查看具体步骤。', result: '从工作台继续追查一次分析或动作生成的结果。' },
    ],
  },
  {
    id: 'leads', title: '线索录入、分配与转化', heading: '线索池', path: '/app/leads', group: '销售工作',
    summary: '把潜在客户整理成线索，明确负责人后转为客户与商机。', prerequisite: '录入至少需要企业名称和联系人；转化前需分配负责人。', guestNote: '可搜索、筛选模拟线索；新增、导入、分配和转化需登录。',
    steps: [
      { title: '查看线索总体情况', target: '[aria-label="线索指标"]', description: '待处理线索包含需要联系或培育的记录。未分配数量用于发现尚无人跟进的线索，平均评分用于快速判断线索质量。', result: '识别先分配还是先联系。' },
      { title: '录入或批量导入', target: '.page-actions', description: '“新建线索”填写企业、联系人、来源和负责人。批量导入选择 CSV，文件需含“企业名称”和“联系人”列，完成后查看成功、跳过和错误提示。', result: '线索进入列表，导入结果可核对。' },
      { title: '搜索与筛选', target: '.toolbar-controls', description: '可按企业、联系人、邮箱或电话搜索，也可用状态下拉框查看新线索、已联系或培育中记录。筛选后检查列表数量。', result: '找到本次准备处理的线索。' },
      { title: '分配负责人并持续维护', target: '.leads-table', description: '在未分配记录右侧点击人员图标，可分配给当前销售。编辑图标用于维护状态、评分和下一步动作；已转化或失效记录不再继续转化。', result: '每条有效线索有明确的销售负责人。' },
      { title: '转为客户和商机', target: '.leads-table', description: '点击行末的转化图标，填写商机名称、预计金额和成交日期后确认。转化会保留客户档案并创建商机，之后可在“客户”和“商机”中继续推进。', result: '线索状态变为已转化，形成客户与商机记录。' },
    ],
  },
  {
    id: 'customers', title: '客户档案与互动记录', heading: '客户', path: '/app/customers', group: '销售工作',
    summary: '统一维护客户、联系人、负责人和历史沟通，把分析结果沉淀为下一步动作。', prerequisite: '先添加客户或从线索转化；AI 分析需要已配置模型和成员额度。', guestNote: '可打开模拟客户档案和互动记录；资料修改、上传和 AI 分析需登录。',
    steps: [
      { title: '理解客户指标', target: '[aria-label="客户指标"]', description: '查看客户总数、高意向客户、活跃商机和平均评分。高意向按客户评分判断，客户数和商机数不是同一个统计口径。', result: '对当前客户池形成整体判断。' },
      { title: '定位负责的客户', target: '.toolbar-controls', description: '搜索企业名称、行业或网站，并按商机阶段筛选。列表同时显示评分、预计金额、负责人和下一步动作。', result: '找到需要查看或维护的客户。' },
      { title: '打开客户档案', target: '.customer-form, .customer-drawer, .workspace-table-wrap', reveal: 'customer-profile', description: '点击客户名称或行末查看/编辑图标打开档案。“客户资料”中可维护企业信息、主要联系人、负责人、跟进时间和下一步动作。', result: '客户信息和销售推进计划集中保存。' },
      { title: '沉淀沟通与附件', target: '.customer-interactions, .customer-drawer', reveal: 'customer-interactions', description: '在客户档案切换“互动记录”，可查看历史沟通。登录后可新增电话/会议记录、导入聊天或上传报价、方案等附件；上传前确认关联客户。', result: '后续分析与邮件发送能使用有来源的沟通和文件。' },
      { title: '确认 AI 分析后再回写', target: '.customer-interactions, .customer-drawer', reveal: 'customer-interactions', description: '在一条聊天记录上点击 AI 分析，检查需求、痛点、风险和分析依据。确认无误后点击“确认并更新客户”，才会更新评分与下一步动作。', result: 'AI 建议经过销售确认，重新分析保留历史版本。' },
    ],
  },
  {
    id: 'opportunities', title: '商机管道与阶段推进', heading: '商机管道', path: '/app/opportunities', group: '销售工作',
    summary: '跟踪销售机会、预计收入、成交概率和阶段变化。', prerequisite: '新建商机需要真实客户和负责人；线索转化也会创建商机。', guestNote: '可切换看板/列表并查看模拟商机；保存与阶段变更需登录。',
    steps: [
      { title: '看金额与风险', target: '[aria-label="商机指标"]', description: '活跃金额是未关闭商机的金额之和，加权金额按成交概率计算。停滞商机帮助发现长时间没有推进的机会。', result: '区分潜在销售规模与概率加权后的预期。' },
      { title: '创建一条商机', target: '.page-actions', description: '点击“新建商机”，选择客户、负责人，填写商机名称、金额、概率和预计成交日期。预测分类用于区分管道、最佳情况和承诺。', result: '商机进入需求确认阶段，后续可编辑和推进。' },
      { title: '切换视图与筛选', target: '.opportunity-panel .module-toolbar', description: '用负责人、阶段和关键词缩小范围。看板按阶段分列，列表便于对照金额、概率、预计成交日期和下一步。', result: '按工作习惯查看同一批商机。' },
      { title: '查看详情并推进', target: '.opportunity-panel', description: '点击商机打开详情，登录后可编辑或变更阶段。赢单会按成交处理，输单必须填写原因；关闭后不再继续变更阶段。', result: '阶段和成交状态与实际销售进度一致。' },
      { title: '核对变更记录', target: '.opportunity-panel', description: '真实商机详情包含变更历史，可查看阶段变化、操作者和时间。如果保存提示资料已更新，刷新后再修改。', result: '推进过程可追溯，并避免覆盖他人的修改。' },
    ],
  },
  {
    id: 'follow-ups', title: '跟进任务与完成确认', heading: '跟进任务', path: '/app/follow-ups', group: '销售工作',
    summary: '把客户下一步动作纳入待办，按时完成并确认结果。', prerequisite: '批准 Agent 生成的内部跟进建议后，任务会进入队列。', guestNote: '可按全部、今天和逾期查看模拟任务；完成确认需登录。',
    steps: [
      { title: '识别优先处理范围', target: '.module-stat-grid', description: '对比当前待办、今日/逾期待办和已逾期数量，先处理已经超过计划时间的事项。', result: '明确今日任务优先级。' },
      { title: '筛选今天或逾期任务', target: '[aria-label="任务筛选"]', description: '点击“全部”“今天”或“已逾期”切换范围。某个范围没有记录时，可回到全部查看其他计划。', result: '只关注本轮需要处理的任务。' },
      { title: '阅读任务内容与负责人', target: '.task-list', description: '核对任务标题、客户、生成原因、优先级和截止时间，再确认负责人。任务内容用于指导实际联系或内部准备。', result: '知道联系谁、做什么、何时完成。' },
      { title: '实际完成后确认', target: '.task-list', description: '完成实际工作后，登录用户可点击任务左侧勾选按钮确认完成。只查看内容时不要提前标记；手动新建任务当前尚未开放。', result: '开放任务队列反映真实剩余工作。' },
    ],
  },
  {
    id: 'forecast', title: '销售预测与目标缺口', heading: '销售预测', path: '/app/forecast', group: '销售工作',
    summary: '理解目标完成度、承诺预测和团队收入缺口。', prerequisite: '当前页面根据演示管道计算，尚未接入正式预测数据。', guestNote: '可切换预测周期并查看演示汇总。',
    steps: [
      { title: '选择预测周期', target: '[aria-label="预测周期"]', description: '切换本月、本季度或本年度，对比同一周期下的目标和预测金额。当前切换用于演示周期汇总。', result: '所有金额在一致的时间范围内比较。' },
      { title: '理解几种预测金额', target: '.forecast-summary', description: '已赢单代表已完成商机；承诺代表销售明确承诺；最佳情况包含承诺机会；加权管道按金额乘成交概率计算。', result: '避免把最佳情况、承诺和实际收入重复相加。' },
      { title: '按负责人看覆盖情况', target: '.forecast-table-panel', description: '逐行对照目标、已赢单、承诺、最佳情况和管道覆盖。风险列提示当前承诺仍无法覆盖目标的缺口。', result: '找到需要补充机会或重点支持的负责人。' },
      { title: '回到商机推动行动', target: '.forecast-insights', description: '查看“距目标仍差”和优先推进商机，再回到商机管道核对下一步动作。预测页面当前不提供修改目标或保存预测。', result: '把预测缺口转成商机推进清单。' },
    ],
  },
  {
    id: 'approvals', title: '高风险审批与邮件闭环', heading: '审批', path: '/app/approvals', group: 'AI 与协作',
    summary: '核对 Agent 提出的动作，批准或拒绝，并检查真实执行结果。', prerequisite: '先由 Agent 或 MCP 产生待审批动作；发邮件需配置邮箱并准备所需附件。', guestNote: '可查看模拟审批与邮件预览；批准、拒绝和真实发送需登录。',
    steps: [
      { title: '识别动作和风险', target: '.approval-list', description: '查看动作类型、客户、风险等级、发起方和生成原因。批准意味着允许执行该项动作，先确认它符合客户沟通目标。', result: '清楚本次授权将影响哪位客户。' },
      { title: '检查邮件和附件', target: '.email-approval-preview, .approval-list', description: '发邮件时逐项核对收件人、主题、正文和附件。方案、报价或合同需要文件时，应先在客户档案或聊天中提供对应文件。', result: '批准前确认发送对象、文字和文件均正确。' },
      { title: '作出批准或拒绝决定', target: '.approval-actions, .approval-list', description: '批准会触发后续工具执行，拒绝则不执行外部动作。MCP 中出现审批时也可直接在聊天内确认，无需切换页面。', result: '审批状态记录本次人工决定。' },
      { title: '区分批准与发送成功', target: '.approval-layout', description: '批准后还要检查结果提示。邮件发送成功会记录 EMAIL_SENT；如果发送失败，按提示核对邮箱、收件人或附件，再查看客户互动和 Agent 记录。', result: '以工具执行结果确认业务闭环，避免重复发送。' },
    ],
  },
  {
    id: 'mcp', title: 'MCP 助手与附件操作', heading: 'AI助手', path: '/app/mcp', group: 'AI 与协作',
    summary: '通过自然语言操作客户、导入聊天、触发分析和查询知识。', prerequisite: '正式执行前需配置 AI 模型并分配成员 Token 额度。', guestNote: '当前展示预设模拟会话；发送指令和文件上传需登录。',
    steps: [
      { title: '先选会话', target: '.mcp-history-panel', description: '从左侧打开已有会话，或新建会话开始新的工作。正式账号的会话会保存；游客看到的是模拟会话。', result: '本轮指令放在正确的业务上下文中。' },
      { title: '输入清晰的业务指令', target: '.mcp-composer textarea', description: '例如“给云岚科技导入聊天：客户希望下周看方案”，或“查看待审批”。提供客户名称、具体内容和目标动作，信息不全时补充后再继续。', result: '助手能识别要调用的业务工具。' },
      { title: '需要文件时再附上', target: '.mcp-composer-tools', description: '点击回形针选择方案、报价或合同，检查附件名称。选择只保留在本地，点击发送时才上传；每条消息最多 5 个附件，单文件不超过 10MB。', result: '发出指令时同时提供操作所需文件。' },
      { title: '核对结果与处理记录', target: '.mcp-message-list', description: '查看结果输出、执行过程和处理记录。每一步会说明正在做什么、是否完成；需要排查时再展开技术详情。失败时先核对业务记录，不要立即重复发送同一指令；知识库回答可通过引用打开资料。', result: '知道实际做了什么，以及哪些步骤还未完成。' },
      { title: '审批与额度', target: '.mcp-history-panel', description: '高风险动作会产生待审批项，可在聊天内核对并批准。用量统计显示成员剩余额度与团队累计；没有分配额度时，正式 MCP 指令会受限。', result: '将执行权限和用量与本次操作联系起来。' },
    ],
  },
  {
    id: 'knowledge', title: '企业知识库与带引用问答', heading: '企业知识库', path: '/app/knowledge', group: 'AI 与协作',
    summary: '上传企业资料，检索相关内容，并查看回答引用的原文。', prerequisite: '支持 PDF、DOCX、TXT 和 Markdown，单文件不超过 10MB；扫描件需先做 OCR。', guestNote: '可查看模拟资料并询问报价审批或产品功能；不调用真实模型。',
    steps: [
      { title: '上传文件或录入文本', target: '.page-actions', reveal: 'knowledge-documents', description: '点击“新增资料”，选择文件或录入正文，填写标题、分类和可见范围。“保存并入库”才上传并解析；个人私有仅上传者可见，团队共享供同组织成员查询。', result: '形成可检索的企业资料。' },
      { title: '检查入库状态', target: '.knowledge-library', reveal: 'knowledge-documents', description: '用标题、文件名或分类定位资料，确认状态为已入库。解析失败时打开详情查看原因，修正文件后重新解析。停用资料不参与问答。', result: '用于回答的资料可用且内容正确。' },
      { title: '提出具体问题', target: '.knowledge-question', reveal: 'knowledge-ask', description: '输入产品、报价或流程问题，例如“发送报价前需要哪些审批？”。选择全部可访问资料或单份资料，再点击提问。正式问答需要模型和成员额度。', result: '回答基于检索到的资料；无相关内容时会明确提示。' },
      { title: '核对回答来源', target: '.knowledge-ask', reveal: 'knowledge-ask', description: '回答下方列出参考资料、片段版本和 PDF 页码。展开“查看引用原文”核对依据，点击来源标题打开详情；游客可先用“报价审批”体验。', result: '能追溯回答依据，而不只看到一段结论。' },
      { title: '维护资料版本', target: '.knowledge-library', reveal: 'knowledge-documents', description: '打开资料详情可下载、编辑、替换原文件、重新解析、停用或删除。修改后核对新版本，删除或停用的内容不会继续参与新问答。', result: '知识内容随着企业资料更新。' },
    ],
  },
  {
    id: 'agent-runs', title: 'Agent 运行与问题追踪', heading: 'Agent 运行', path: '/app/agent-runs', group: 'AI 与协作',
    summary: '从一次运行追踪模型分析、业务步骤、审批与最终结果。', prerequisite: '先导入客户聊天并触发分析，或通过 MCP 运行客户跟进 Agent。', guestNote: '可查看模拟运行和步骤；触发真实运行需登录。',
    steps: [
      { title: '先看运行指标', target: '[aria-label="Agent 运行指标"]', description: '查看今日运行、处理客户、待审批和失败数量。待审批不是失败，它表示动作在等待人工决定。', result: '分清待处理状态与执行异常。' },
      { title: '选择要追查的运行', target: '.run-history-panel', description: '点击一条运行记录，核对名称、状态、时间和触发方式。由 MCP 触发的运行也会标识来源。', result: '定位到本次操作对应的记录。' },
      { title: '按步骤查看执行过程', target: '.execution-flow, .run-detail-panel', description: '对照每一步的状态和耗时，确认分析或工具执行在哪一步结束。如果出现失败，结合客户记录核查，不要直接重复外部发送。', result: '缩小需要排查的环节。' },
      { title: '把运行结果接回业务', target: '.run-facts, .run-detail-panel', description: '核对处理范围、客户数量、待审批数和运行摘要。需要人工确认的动作进入审批页，成功产生的跟进任务进入跟进队列。', result: '完成从分析到人工确认再到业务记录的追踪。' },
    ],
  },
  {
    id: 'analytics', title: '效果分析与统计口径', heading: '效果分析', path: '/app/analytics', group: 'AI 与协作',
    summary: '理解触达、回复、建议采纳与时间成本的统计含义。', prerequisite: '当前页面为演示数据，尚未接入完整正式统计。', guestNote: '可浏览指标、趋势、漏斗与计算口径。',
    steps: [
      { title: '阅读整体效果指标', target: '.analytics-stats', description: '查看节省时间、触达量、有效回复率和投入产出比。金额或工时相关指标应结合页面展示的估算口径理解。', result: '知道每个指标试图衡量什么。' },
      { title: '查看触达趋势', target: '.chart-panel', description: '柱状图展示每日客户触达分布，用于比较哪些日期更活跃。当前为预设演示趋势。', result: '识别触达量的时间分布。' },
      { title: '寻找漏斗流失', target: '.funnel-panel', description: '按识别机会、生成建议、人工批准、成功触达和获得回复查看逐层变化，关注哪一层转化较低。', result: '区分建议质量、人工采纳和客户响应问题。' },
      { title: '核对计算口径', target: '.analytics-table-panel', description: '查看业务效果明细中的 Agent 值、人工基线与变化。当前导出按钮未开放，正式使用前需要接入真实统计。', result: '解释数字时有明确依据。' },
    ],
  },
  {
    id: 'settings', title: '模型、邮箱与审批设置', heading: '设置', path: '/app/settings', group: '配置与管理',
    summary: '准备 AI 调用和邮件发送所需的配置，并检查连接状态。', prerequisite: '准备千问兼容接口地址、模型与 API Key；发邮件还需邮箱 SMTP 授权信息。', guestNote: '配置项仅供查看，保存与测试连接需登录。',
    steps: [
      { title: '查看系统连接', target: '#integrations', description: '先检查业务 API 是否可连接，再分别准备模型与发件邮箱。游客状态中的服务信息均为演示。', result: '知道问题来自业务服务还是外部通道。' },
      { title: '配置并测试 AI 模型', target: '#model', description: '填写模型名称、API Key 和 HTTPS 兼容地址，保存后测试连接。修改已保存的配置时，密钥留空代表保留原值。成员还需分配额度才能使用 AI。', result: '模型就绪后用于新产生的分析与聊天调用。' },
      { title: '配置个人发件邮箱', target: '#email-settings', description: '填写 SMTP 主机、端口、账号、授权码和发件人，按邮箱要求设置认证与 STARTTLS，保存后测试连接。常见邮箱使用授权码而不是网页登录密码。', result: '审批通过的邮件有可用发送通道。' },
      { title: '理解审批规则', target: '#approval-policy', description: '查看客户消息、敏感 CRM 更新和内部任务的审批选项。当前策略区为界面展示，复选项尚未接入独立保存接口。', result: '不把界面切换误认为策略已经生效。' },
    ],
  },
  {
    id: 'token-quota', title: '团队 Token 预算与成员分配', heading: 'AI助手', path: '/app/mcp', group: '配置与管理', adminOnly: true,
    summary: '先确定团队总额，再为成员分配可用额度。', prerequisite: '需要所有者或管理员权限；分配入口在 MCP 左侧用量统计。', guestNote: '此功能仅向管理员显示。',
    steps: [
      { title: '打开 Token 分配', target: '.mcp-quota-button', allowInteraction: true, description: '在用量统计中点击“Token 分配”。这里维护团队内部使用预算，不代表模型服务商账户余额。', result: '进入预算与成员分配面板。' },
      { title: '先保存团队总额', target: '.mcp-token-budget', reveal: 'token-budget', description: '在分配面板输入团队 Token 总额并保存。成员分配不能超出团队剩余可分配量，调整前先核对已分配和已用数量。', result: '团队有明确的内部预算上限。' },
      { title: '为成员配置额度', target: '.mcp-token-member-list', reveal: 'token-budget', description: '逐一输入成员分配额并保存，参考每位成员旁的可分配上限和已用量。没有分配额度或剩余额度不足时，成员不能执行正式 AI 请求。', result: '额度分配到具体成员。' },
      { title: '跟踪实际使用', target: '.mcp-history-panel', reveal: 'token-usage', description: '返回用量统计查看个人剩余比例、团队累计和调用次数。模型调用会累计输入与输出 Token；服务商余额需要在供应商平台核查。', result: '根据团队消耗继续调整内部预算。' },
    ],
  },
  {
    id: 'members', title: '成员账号与角色权限', heading: '成员管理', path: '/app/admin/members', group: '配置与管理', adminOnly: true,
    summary: '建立团队账号，配置角色并维护账号状态。', prerequisite: '需要所有者或管理员权限；管理员可管理的对象受角色限制。', guestNote: '此功能仅向管理员显示。',
    steps: [
      { title: '查找成员', target: '.admin-filters', description: '按姓名、邮箱、角色或账号状态筛选，核对成员身份后再修改。', result: '选中正确的账号。' },
      { title: '添加登录账号', target: '.page-actions', description: '点击“添加成员”，填写显示名称、邮箱、初始密码和角色。账号创建后可用对应邮箱与密码登录。', result: '新成员进入当前工作区。' },
      { title: '管理角色与状态', target: '.admin-table-wrap', description: '点击“管理”查看可编辑信息；根据职责配置主管、销售或只读角色，必要时停用账号。不能随意修改所有者或超出权限范围的管理员。', result: '成员权限与实际职责一致。' },
      { title: '补充 AI 使用额度', target: '.admin-summary-strip', description: '创建成员不等于分配 AI 额度。需要 AI 的成员还应到 MCP 的“Token 分配”配置预算，并检查模型已就绪。', result: '账号、业务角色和 AI 使用权限都已准备好。' },
    ],
  },
  {
    id: 'team', title: '团队资料与区域设置', heading: '团队管理', path: '/app/admin/team', group: '配置与管理', adminOnly: true,
    summary: '维护工作区名称、业务时区和语言配置。', prerequisite: '需要所有者或管理员权限。', guestNote: '此功能仅向管理员显示。',
    steps: [
      { title: '查看团队概况', target: '.admin-metric-grid', description: '核对成员数、活跃账号与管理员数量，区分账号总量和实际可登录成员。', result: '了解当前团队结构。' },
      { title: '维护名称与区域设置', target: '.admin-team-card', description: '修改团队名称、业务时区和默认语言。工作区标识保持稳定，不能在这里修改；业务时区用于业务时间计算。', result: '工作区基础信息与团队实际情况一致。' },
      { title: '保存并核对', target: '.settings-form-actions', description: '点击保存后检查成功提示。团队名称在重新登录后同步到侧栏；不要把未保存的输入当成已生效配置。', result: '团队信息已持久化。' },
      { title: '了解管理边界', target: '.admin-policy-card', description: '所有者可以管理管理员和团队；管理员主要管理普通成员；业务角色不会看到管理员入口。', result: '知道哪些操作应由谁完成。' },
    ],
  },
  {
    id: 'audit', title: '操作日志查询与审计', heading: '日志管理', path: '/app/audit-logs', group: '配置与管理', adminOnly: true,
    summary: '按操作者、动作、资源和结果定位系统中的业务操作。', prerequisite: '需要所有者或管理员权限。', guestNote: '此功能仅向管理员显示。',
    steps: [
      { title: '确定要查的操作', target: '.audit-filters', description: '先按关键词搜索，再用动作类型、资源类型和结果筛选。尽量从明确的客户、操作者或操作时间开始。', result: '将日志范围缩小到相关事件。' },
      { title: '核对日志记录', target: '.audit-table-wrap', description: '逐项核对时间、操作者、动作、目标资源和执行结果。失败日志表示该次操作未按预期完成，需要结合业务页面核查。', result: '找到可追踪的操作记录。' },
      { title: '分页查看历史', target: '.table-pagination', description: '使用分页读取更早记录，调整筛选后从第一页重新查看。查无记录时检查关键词与筛选范围。', result: '完整查看符合条件的记录。' },
      { title: '结合业务与运行记录排查', target: '.audit-panel', description: '日志用于追踪操作，Agent 运行用于查看分析步骤，客户互动用于核对真实沟通。对照这些记录确认最终结果后再决定是否重试。', result: '把操作日志和业务结果联系起来。' },
    ],
  },
]

export function availableGuides(role: string) {
  return featureGuides.filter(guide => !guide.adminOnly || role === 'OWNER' || role === 'ADMIN')
}
