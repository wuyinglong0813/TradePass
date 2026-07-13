/**
 * 数字金额转中文大写
 * @param {number} num
 * @returns {string} 如 "壹万贰仟叁佰肆拾伍元陆角柒分"
 */
function numberToChineseCurrency(num) {
  if (num === null || num === undefined || isNaN(num) || num < 0) return '零元整';
  if (num === 0) return '零元整';

  const digits = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'];
  const radices = ['', '拾', '佰', '仟'];
  const bigRadices = ['', '万', '亿', '兆'];

  // 处理到分
  const amount = Math.round(num * 100);
  const intPart = Math.floor(amount / 100);
  const decPart = amount % 100;

  let result = '';

  // 处理整数部分（每4位一组）
  if (intPart > 0) {
    let zeroCount = 0;
    const intStr = String(intPart);
    const len = intStr.length;
    let needZero = false;

    for (let i = 0; i < len; i++) {
      const p = len - 1 - i;           // 从右往左的位置
      const d = parseInt(intStr[i]);    // 当前数字
      const q = Math.floor(p / 4);      // 大单位索引（万/亿）
      const r = p % 4;                  // 小单位索引（仟/佰/拾）

      if (d === 0) {
        zeroCount++;
      } else {
        if (zeroCount > 0 && needZero) {
          result += '零';
        }
        result += digits[d] + radices[r];
        zeroCount = 0;
        needZero = true;
      }

      // 在每4位末尾（r===0 即个位）加上大单位
      if (r === 0) {
        if (zeroCount < 4) {
          result += bigRadices[q];
        }
        zeroCount = 0;
        needZero = false;
      }
    }
    result += '元';
  }

  // 处理小数部分
  const jiao = Math.floor(decPart / 10);
  const fen = decPart % 10;

  if (jiao === 0 && fen === 0) {
    result += '整';
  } else {
    if (jiao > 0) {
      result += digits[jiao] + '角';
    } else if (intPart > 0) {
      result += '零';
    }
    if (fen > 0) {
      result += digits[fen] + '分';
    }
  }

  return result;
}

/**
 * 默认购销合同模板 JSON
 */
const DEFAULT_TEMPLATE = {
  title: '购销合同',
  fields: [
    { key: 'contractNo', label: '合同编号', value: '', editable: false, hint: '签订时自动生成' },
    { key: 'supplier', label: '供方（甲方）', value: '', editable: true },
    { key: 'buyer', label: '需方（乙方）', value: '', editable: true },
    { key: 'signDate', label: '签订日期', value: '', editable: true, type: 'date' }
  ],
  sections: [
    {
      title: '一、产品名称、规格、数量、单价、金额',
      type: 'table',
      columns: ['产品名称', '规格型号', '单位', '数量', '单价(元)', '金额(元)'],
      rows: [['', '', '', '0', '0', '0']]
    },
    { title: '二、质量要求、技术标准', type: 'clause', content: '' },
    { title: '三、交货时间、地点、方式', type: 'clause', content: '' },
    { title: '四、运输方式及费用承担', type: 'clause', content: '' },
    { title: '五、包装标准及费用', type: 'clause', content: '' },
    { title: '六、验收标准、方法', type: 'clause', content: '' },
    { title: '七、结算方式及期限', type: 'clause', content: '' },
    { title: '八、违约责任', type: 'clause', content: '' },
    { title: '九、合同争议解决方式', type: 'clause', content: '' },
    { title: '十、合同生效与变更', type: 'clause', content: '' },
    { title: '十一、其他约定事项', type: 'clause', content: '' }
  ]
};

/**
 * 计算产品表格合计
 * @param {Array<Array<string>>} rows - 每行 [产品名称, 规格, 单位, 数量, 单价, 金额]
 * @returns {{ rows, totalAmount: number, totalAmountCn: string }}
 */
function calcTableTotal(rows) {
  let total = 0;
  const calcRows = (rows || []).map(row => {
    const qty = parseFloat(row[3]) || 0;
    const price = parseFloat(row[4]) || 0;
    const amount = Math.round(qty * price * 100) / 100;
    total += amount;
    return [row[0] || '', row[1] || '', row[2] || '', row[3] || '0', row[4] || '0', String(amount)];
  });
  return {
    rows: calcRows,
    totalAmount: Math.round(total * 100) / 100,
    totalAmountCn: numberToChineseCurrency(Math.round(total * 100) / 100)
  };
}

const CHINESE_NUMS = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十',
  '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八', '十九', '二十',
  '二十一', '二十二', '二十三', '二十四', '二十五', '二十六', '二十七', '二十八', '二十九', '三十'];

/**
 * 数字转中文序号（1→一，11→十一）
 */
function toChineseNum(n) {
  const idx = Math.max(0, n - 1);
  return CHINESE_NUMS[idx] || String(n);
}

/**
 * 重新编排条款序号
 * @param {Array<{title:string,content:string}>} clauses
 * @returns {Array} 重新编号后的条款（从"二"开始，因为"一"是产品表格）
 */
function reorderClauses(clauses) {
  return (clauses || []).map((c, i) => ({
    title: c.title || '',
    content: c.content || '',
    _num: toChineseNum(i + 2),
    _label: `${toChineseNum(i + 2)}、`
  }));
}

module.exports = { numberToChineseCurrency, DEFAULT_TEMPLATE, calcTableTotal, toChineseNum, reorderClauses };
