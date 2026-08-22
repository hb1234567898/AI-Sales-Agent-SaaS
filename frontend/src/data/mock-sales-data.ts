export const mockCustomers = [
  { id: 'CUS-1042', company: '云岚科技', contact: '林婉清', stage: '方案确认', owner: '陈默', score: 92, value: '¥ 48.6 万', lastTouch: '18 分钟前', nextAction: '发送方案确认邮件', industry: '企业服务' },
  { id: 'CUS-1038', company: '恒川智造', contact: '周启明', stage: '技术评审', owner: '李昕', score: 88, value: '¥ 76.2 万', lastTouch: '1 小时前', nextAction: '确认技术评审时间', industry: '智能制造' },
  { id: 'CUS-1029', company: '北辰零售', contact: '宋雨', stage: '产品试用', owner: '陈默', score: 84, value: '¥ 31.8 万', lastTouch: '昨天 16:40', nextAction: '跟进试用反馈', industry: '新零售' },
  { id: 'CUS-1024', company: '澄海数据', contact: '许哲', stage: '价值评估', owner: '王宁', score: 76, value: '¥ 22.4 万', lastTouch: '昨天 11:20', nextAction: '补充 ROI 测算', industry: '数据服务' },
  { id: 'CUS-1017', company: '拓维物流', contact: '韩知远', stage: '需求确认', owner: '李昕', score: 71, value: '¥ 19.6 万', lastTouch: '2 天前', nextAction: '重新确认采购窗口', industry: '智慧物流' },
  { id: 'CUS-1011', company: '星桥教育', contact: '沈禾', stage: '初步接触', owner: '王宁', score: 63, value: '¥ 12.8 万', lastTouch: '3 天前', nextAction: '发送行业案例', industry: '职业教育' },
  { id: 'CUS-1006', company: '沐光医疗', contact: '苏恬', stage: '培育中', owner: '陈默', score: 58, value: '¥ 27.3 万', lastTouch: '5 天前', nextAction: '确认预算周期', industry: '医疗科技' },
]

export const mockFollowUps = [
  { id: 'TASK-318', title: '发送方案确认邮件', company: '云岚科技', contact: '林婉清', due: '今天 11:30', priority: '高', owner: '陈默', source: 'AI 建议', status: '待处理' },
  { id: 'TASK-317', title: '确认技术评审时间', company: '恒川智造', contact: '周启明', due: '今天 14:00', priority: '高', owner: '李昕', source: '会议纪要', status: '待处理' },
  { id: 'TASK-314', title: '跟进试用反馈', company: '北辰零售', contact: '宋雨', due: '今天 16:30', priority: '高', owner: '陈默', source: 'CRM 规则', status: '待处理' },
  { id: 'TASK-309', title: '补充 ROI 测算', company: '澄海数据', contact: '许哲', due: '昨天 17:00', priority: '中', owner: '王宁', source: 'AI 建议', status: '已逾期' },
  { id: 'TASK-305', title: '重新确认采购窗口', company: '拓维物流', contact: '韩知远', due: '明天 10:00', priority: '中', owner: '李昕', source: 'CRM 规则', status: '待处理' },
  { id: 'TASK-301', title: '发送行业案例', company: '星桥教育', contact: '沈禾', due: '周五 15:00', priority: '低', owner: '王宁', source: '人工创建', status: '待处理' },
]

export const mockApprovals = [
  { id: 'APR-087', company: '云岚科技', action: '发送方案确认邮件', type: '客户消息', risk: '中', requester: 'Sales Agent', createdAt: '10:26', confidence: '92%', reason: '邮件包含价格和交付周期，需要人工确认。' },
  { id: 'APR-086', company: '恒川智造', action: '更新商机阶段为技术评审', type: 'CRM 更新', risk: '低', requester: 'Sales Agent', createdAt: '09:51', confidence: '87%', reason: '会议纪要中出现明确的技术评审安排。' },
  { id: 'APR-083', company: '北辰零售', action: '发送试用到期提醒', type: '客户消息', risk: '中', requester: 'Sales Agent', createdAt: '昨天 16:45', confidence: '84%', reason: '客户尚未确认是否延长试用，需要检查措辞。' },
]

export const mockRuns = [
  { id: 'RUN-0241', name: '高意向客户分析', trigger: '定时规则', scope: '42 个客户', startedAt: '今天 10:24', duration: '34 秒', status: '已完成', model: 'qwen-plus', tokens: '18,420', cost: '¥ 0.42', result: '识别 4 个高优先级客户，生成 8 条跟进建议。' },
  { id: 'RUN-0240', name: '跟进动作生成', trigger: '人工触发', scope: '8 个客户', startedAt: '今天 09:48', duration: '21 秒', status: '待审核', model: 'qwen-plus', tokens: '9,870', cost: '¥ 0.23', result: '生成 5 封邮件和 3 条 CRM 更新建议。' },
  { id: 'RUN-0239', name: '沉默客户识别', trigger: '定时规则', scope: '126 个客户', startedAt: '今天 08:35', duration: '1 分 12 秒', status: '已完成', model: 'qwen-turbo', tokens: '31,560', cost: '¥ 0.38', result: '识别 17 个超过 14 天未互动的客户。' },
  { id: 'RUN-0238', name: '会议纪要解析', trigger: 'Webhook', scope: '6 份纪要', startedAt: '昨天 17:20', duration: '16 秒', status: '已完成', model: 'qwen-plus', tokens: '7,210', cost: '¥ 0.18', result: '提取 12 个行动项并关联到 5 个客户。' },
  { id: 'RUN-0237', name: '客户评分更新', trigger: 'CRM 变更', scope: '68 个客户', startedAt: '昨天 15:04', duration: '48 秒', status: '部分失败', model: 'qwen-turbo', tokens: '22,840', cost: '¥ 0.29', result: '成功更新 65 个客户，3 个客户缺少联系人信息。' },
]
