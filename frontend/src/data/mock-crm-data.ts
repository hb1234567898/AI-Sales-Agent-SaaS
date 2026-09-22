export type LeadStatus = 'NEW' | 'NURTURING' | 'QUALIFIED' | 'DISQUALIFIED'
export type OpportunityStage = 'DISCOVERY' | 'DEMO' | 'PROPOSAL' | 'NEGOTIATION' | 'WON'
export type ForecastCategory = 'PIPELINE' | 'BEST_CASE' | 'COMMIT'

export interface SalesLead {
  id: string
  company: string
  contact: string
  title: string
  source: string
  status: LeadStatus
  score: number
  owner: string | null
  createdAt: string
  lastActivity: string
}

export interface SalesOpportunity {
  id: string
  company: string
  name: string
  stage: OpportunityStage
  amount: number
  probability: number
  owner: string
  closeDate: string
  nextStep: string
  forecastCategory: ForecastCategory
  daysInStage: number
}

export const leadStatusLabels: Record<LeadStatus, string> = {
  NEW: '待处理',
  NURTURING: '培育中',
  QUALIFIED: '已转化',
  DISQUALIFIED: '已淘汰',
}

export const opportunityStageLabels: Record<OpportunityStage, string> = {
  DISCOVERY: '需求调研',
  DEMO: '产品验证',
  PROPOSAL: '方案报价',
  NEGOTIATION: '商务谈判',
  WON: '赢单',
}

export const forecastCategoryLabels: Record<ForecastCategory, string> = {
  PIPELINE: '管道',
  BEST_CASE: '最佳情况',
  COMMIT: '承诺',
}

export const mockLeads: SalesLead[] = [
  { id: 'LEAD-1088', company: '远川工业', contact: '谢明远', title: '信息化负责人', source: '官网表单', status: 'NEW', score: 91, owner: null, createdAt: '今天 09:42', lastActivity: '下载智能制造方案' },
  { id: 'LEAD-1087', company: '青禾生物', contact: '顾清', title: '市场总监', source: '市场活动', status: 'NEW', score: 84, owner: '陈默', createdAt: '今天 08:15', lastActivity: '参加医疗行业闭门会' },
  { id: 'LEAD-1084', company: '顺达供应链', contact: '周航', title: '运营副总裁', source: '客户转介绍', status: 'NURTURING', score: 78, owner: '李昕', createdAt: '昨天 16:26', lastActivity: '回复产品介绍邮件' },
  { id: 'LEAD-1081', company: '棱镜数据', contact: '唐婧', title: '采购经理', source: '内容下载', status: 'NURTURING', score: 72, owner: '王宁', createdAt: '09/12 14:20', lastActivity: '查看报价白皮书' },
  { id: 'LEAD-1079', company: '启明教育', contact: '沈川', title: '校企合作负责人', source: '广告投放', status: 'DISQUALIFIED', score: 43, owner: '王宁', createdAt: '09/11 11:08', lastActivity: '预算周期不明确' },
  { id: 'LEAD-1076', company: '衡岳能源', contact: '赵启', title: '数字化平台主管', source: '合作伙伴', status: 'QUALIFIED', score: 88, owner: '陈默', createdAt: '09/10 17:34', lastActivity: '已创建销售商机' },
]

export const mockOpportunities: SalesOpportunity[] = [
  { id: 'OPP-2048', company: '恒川智造', name: '集团销售数字化一期', stage: 'NEGOTIATION', amount: 762000, probability: 80, owner: '李昕', closeDate: '09/28', nextStep: '确认合同付款条款', forecastCategory: 'COMMIT', daysInStage: 5 },
  { id: 'OPP-2044', company: '云岚科技', name: '客户运营平台升级', stage: 'PROPOSAL', amount: 486000, probability: 65, owner: '陈默', closeDate: '10/15', nextStep: '发送最终报价版本', forecastCategory: 'BEST_CASE', daysInStage: 8 },
  { id: 'OPP-2041', company: '北辰零售', name: '区域门店试点项目', stage: 'DEMO', amount: 318000, probability: 45, owner: '陈默', closeDate: '10/30', nextStep: '收集试用部门反馈', forecastCategory: 'PIPELINE', daysInStage: 12 },
  { id: 'OPP-2039', company: '澄海数据', name: '企业客户协同项目', stage: 'PROPOSAL', amount: 224000, probability: 60, owner: '王宁', closeDate: '10/22', nextStep: '补充 ROI 测算', forecastCategory: 'BEST_CASE', daysInStage: 4 },
  { id: 'OPP-2035', company: '拓维物流', name: '销售过程标准化', stage: 'DISCOVERY', amount: 196000, probability: 25, owner: '李昕', closeDate: '11/18', nextStep: '确认决策链与采购窗口', forecastCategory: 'PIPELINE', daysInStage: 16 },
  { id: 'OPP-2032', company: '星桥教育', name: '招生客户管理项目', stage: 'DISCOVERY', amount: 128000, probability: 20, owner: '王宁', closeDate: '11/30', nextStep: '安排业务需求访谈', forecastCategory: 'PIPELINE', daysInStage: 7 },
  { id: 'OPP-2027', company: '沐光医疗', name: '渠道销售协同平台', stage: 'DEMO', amount: 273000, probability: 50, owner: '陈默', closeDate: '10/18', nextStep: '演示权限与审计功能', forecastCategory: 'BEST_CASE', daysInStage: 6 },
  { id: 'OPP-2019', company: '启航科技', name: '年度 CRM 采购', stage: 'WON', amount: 356000, probability: 100, owner: '李昕', closeDate: '09/08', nextStep: '启动项目交付', forecastCategory: 'COMMIT', daysInStage: 0 },
]

export const salesTargets = {
  team: 3200000,
  owners: {
    '陈默': 1200000,
    '李昕': 1100000,
    '王宁': 900000,
  },
}
