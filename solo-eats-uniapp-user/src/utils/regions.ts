/**
 * 收货地址省市区精简表。
 *
 * 后端 SaveAddressRequest 对 province/city/district 只做长度校验（≤ 64 字符），
 * 不校验行政区划是否真实存在，因此这里不引入完整行政区划数据（体积大、易过期），
 * 只收录常用省份与主要城市。未收录的区县由用户填写在详细地址里。
 */

export interface RegionCity {
  name: string
  districts: string[]
}

export interface RegionProvince {
  name: string
  cities: RegionCity[]
}

export const REGION_PROVINCES: RegionProvince[] = [
  {
    name: '北京市',
    cities: [
      { name: '北京市', districts: ['东城区', '西城区', '朝阳区', '海淀区', '丰台区', '通州区'] },
    ],
  },
  {
    name: '上海市',
    cities: [
      {
        name: '上海市',
        districts: ['黄浦区', '徐汇区', '长宁区', '静安区', '浦东新区', '闵行区'],
      },
    ],
  },
  {
    name: '天津市',
    cities: [{ name: '天津市', districts: ['和平区', '河西区', '南开区', '滨海新区'] }],
  },
  {
    name: '重庆市',
    cities: [{ name: '重庆市', districts: ['渝中区', '江北区', '南岸区', '沙坪坝区', '九龙坡区'] }],
  },
  {
    name: '河北省',
    cities: [
      { name: '石家庄市', districts: ['长安区', '桥西区', '新华区', '裕华区'] },
      { name: '唐山市', districts: ['路南区', '路北区', '丰润区'] },
      { name: '保定市', districts: ['竞秀区', '莲池区'] },
    ],
  },
  {
    name: '山西省',
    cities: [
      { name: '太原市', districts: ['小店区', '迎泽区', '杏花岭区'] },
      { name: '大同市', districts: ['平城区', '云冈区'] },
      { name: '运城市', districts: ['盐湖区'] },
    ],
  },
  {
    name: '内蒙古自治区',
    cities: [
      { name: '呼和浩特市', districts: ['新城区', '回民区', '玉泉区', '赛罕区'] },
      { name: '包头市', districts: ['昆都仑区', '青山区'] },
      { name: '鄂尔多斯市', districts: ['东胜区'] },
    ],
  },
  {
    name: '辽宁省',
    cities: [
      { name: '沈阳市', districts: ['和平区', '沈河区', '铁西区', '皇姑区'] },
      { name: '大连市', districts: ['中山区', '西岗区', '沙河口区', '甘井子区'] },
      { name: '鞍山市', districts: ['铁东区'] },
    ],
  },
  {
    name: '吉林省',
    cities: [
      { name: '长春市', districts: ['南关区', '朝阳区', '绿园区', '净月区'] },
      { name: '吉林市', districts: ['昌邑区', '船营区'] },
      { name: '延边州', districts: ['延吉市'] },
    ],
  },
  {
    name: '黑龙江省',
    cities: [
      { name: '哈尔滨市', districts: ['南岗区', '道里区', '松北区', '香坊区'] },
      { name: '齐齐哈尔市', districts: ['龙沙区', '建华区'] },
      { name: '大庆市', districts: ['萨尔图区'] },
    ],
  },
  {
    name: '江苏省',
    cities: [
      { name: '南京市', districts: ['玄武区', '秦淮区', '鼓楼区', '建邺区', '江宁区'] },
      { name: '苏州市', districts: ['姑苏区', '工业园区', '吴中区', '昆山市'] },
      { name: '无锡市', districts: ['梁溪区', '滨湖区', '新吴区'] },
      { name: '常州市', districts: ['天宁区', '钟楼区', '武进区'] },
    ],
  },
  {
    name: '浙江省',
    cities: [
      { name: '杭州市', districts: ['上城区', '拱墅区', '西湖区', '滨江区', '余杭区'] },
      { name: '宁波市', districts: ['海曙区', '江北区', '鄞州区'] },
      { name: '温州市', districts: ['鹿城区', '龙湾区', '瓯海区'] },
      { name: '嘉兴市', districts: ['南湖区', '秀洲区'] },
    ],
  },
  {
    name: '安徽省',
    cities: [
      { name: '合肥市', districts: ['庐阳区', '蜀山区', '包河区', '瑶海区'] },
      { name: '芜湖市', districts: ['镜湖区', '弋江区'] },
      { name: '黄山市', districts: ['屯溪区'] },
    ],
  },
  {
    name: '福建省',
    cities: [
      { name: '福州市', districts: ['鼓楼区', '台江区', '仓山区', '晋安区'] },
      { name: '厦门市', districts: ['思明区', '湖里区', '集美区', '海沧区'] },
      { name: '泉州市', districts: ['鲤城区', '丰泽区', '晋江市'] },
    ],
  },
  {
    name: '江西省',
    cities: [
      { name: '南昌市', districts: ['东湖区', '西湖区', '青山湖区', '红谷滩区'] },
      { name: '赣州市', districts: ['章贡区', '南康区'] },
      { name: '景德镇市', districts: ['珠山区'] },
    ],
  },
  {
    name: '山东省',
    cities: [
      { name: '济南市', districts: ['历下区', '市中区', '槐荫区', '历城区'] },
      { name: '青岛市', districts: ['市南区', '市北区', '李沧区', '崂山区', '黄岛区'] },
      { name: '烟台市', districts: ['芝罘区', '福山区', '莱山区'] },
      { name: '潍坊市', districts: ['潍城区', '奎文区'] },
    ],
  },
  {
    name: '河南省',
    cities: [
      { name: '郑州市', districts: ['中原区', '二七区', '金水区', '郑东新区'] },
      { name: '洛阳市', districts: ['涧西区', '西工区', '洛龙区'] },
      { name: '开封市', districts: ['龙亭区', '鼓楼区'] },
    ],
  },
  {
    name: '湖北省',
    cities: [
      { name: '武汉市', districts: ['江岸区', '江汉区', '武昌区', '洪山区', '东湖高新区'] },
      { name: '宜昌市', districts: ['西陵区', '伍家岗区'] },
      { name: '襄阳市', districts: ['襄城区', '樊城区'] },
    ],
  },
  {
    name: '湖南省',
    cities: [
      { name: '长沙市', districts: ['芙蓉区', '天心区', '岳麓区', '开福区', '雨花区'] },
      { name: '株洲市', districts: ['天元区', '芦淞区'] },
      { name: '湘潭市', districts: ['雨湖区', '岳塘区'] },
    ],
  },
  {
    name: '广东省',
    cities: [
      { name: '广州市', districts: ['越秀区', '荔湾区', '天河区', '海珠区', '白云区', '番禺区'] },
      { name: '深圳市', districts: ['福田区', '罗湖区', '南山区', '宝安区', '龙岗区', '龙华区'] },
      { name: '珠海市', districts: ['香洲区', '金湾区'] },
      { name: '佛山市', districts: ['禅城区', '南海区', '顺德区'] },
      { name: '东莞市', districts: ['南城街道', '东城街道', '长安镇'] },
    ],
  },
  {
    name: '广西壮族自治区',
    cities: [
      { name: '南宁市', districts: ['青秀区', '兴宁区', '西乡塘区'] },
      { name: '柳州市', districts: ['城中区', '鱼峰区'] },
      { name: '桂林市', districts: ['秀峰区', '象山区'] },
    ],
  },
  {
    name: '海南省',
    cities: [
      { name: '海口市', districts: ['龙华区', '美兰区', '琼山区'] },
      { name: '三亚市', districts: ['吉阳区', '天涯区'] },
    ],
  },
  {
    name: '四川省',
    cities: [
      { name: '成都市', districts: ['锦江区', '青羊区', '金牛区', '武侯区', '成华区', '高新区'] },
      { name: '绵阳市', districts: ['涪城区', '游仙区'] },
      { name: '德阳市', districts: ['旌阳区'] },
    ],
  },
  {
    name: '贵州省',
    cities: [
      { name: '贵阳市', districts: ['南明区', '云岩区', '观山湖区'] },
      { name: '遵义市', districts: ['红花岗区', '汇川区'] },
      { name: '六盘水市', districts: ['钟山区'] },
    ],
  },
  {
    name: '云南省',
    cities: [
      { name: '昆明市', districts: ['五华区', '盘龙区', '官渡区', '西山区'] },
      { name: '大理州', districts: ['大理市'] },
      { name: '丽江市', districts: ['古城区'] },
    ],
  },
  {
    name: '西藏自治区',
    cities: [
      { name: '拉萨市', districts: ['城关区', '堆龙德庆区'] },
      { name: '日喀则市', districts: ['桑珠孜区'] },
      { name: '林芝市', districts: ['巴宜区'] },
    ],
  },
  {
    name: '陕西省',
    cities: [
      { name: '西安市', districts: ['新城区', '碑林区', '莲湖区', '雁塔区', '高新区'] },
      { name: '宝鸡市', districts: ['渭滨区', '金台区'] },
      { name: '咸阳市', districts: ['秦都区', '渭城区'] },
    ],
  },
  {
    name: '甘肃省',
    cities: [
      { name: '兰州市', districts: ['城关区', '七里河区', '安宁区'] },
      { name: '天水市', districts: ['秦州区', '麦积区'] },
      { name: '酒泉市', districts: ['肃州区'] },
    ],
  },
  {
    name: '青海省',
    cities: [
      { name: '西宁市', districts: ['城中区', '城东区', '城西区', '城北区'] },
      { name: '海东市', districts: ['乐都区', '平安区'] },
    ],
  },
  {
    name: '宁夏回族自治区',
    cities: [
      { name: '银川市', districts: ['兴庆区', '金凤区', '西夏区'] },
      { name: '吴忠市', districts: ['利通区'] },
      { name: '中卫市', districts: ['沙坡头区'] },
    ],
  },
  {
    name: '新疆维吾尔自治区',
    cities: [
      { name: '乌鲁木齐市', districts: ['天山区', '沙依巴克区', '新市区', '水磨沟区'] },
      { name: '克拉玛依市', districts: ['独山子区', '克拉玛依区'] },
      { name: '伊犁州', districts: ['伊宁市'] },
    ],
  },
  {
    name: '香港特别行政区',
    cities: [
      { name: '香港岛', districts: ['中西区', '湾仔区', '东区'] },
      { name: '九龙', districts: ['油尖旺区', '深水埗区', '九龙城区'] },
      { name: '新界', districts: ['沙田区', '荃湾区', '元朗区'] },
    ],
  },
  {
    name: '澳门特别行政区',
    cities: [
      { name: '澳门半岛', districts: ['花地玛堂区', '圣安多尼堂区', '大堂区'] },
      { name: '氹仔', districts: ['嘉模堂区'] },
      { name: '路环', districts: ['圣方济各堂区'] },
    ],
  },
  {
    name: '台湾省',
    cities: [
      { name: '台北市', districts: ['中正区', '信义区', '大安区'] },
      { name: '台中市', districts: ['西屯区', '北区'] },
      { name: '高雄市', districts: ['苓雅区', '左营区'] },
    ],
  },
]

/** 按名称定位省/市/区索引，找不到时返回 0，用于编辑页回填 picker。 */
export function findRegionIndexes(
  provinceName: string | null,
  cityName: string | null,
  districtName: string | null,
): [number, number, number] {
  const provinceIndex = Math.max(
    0,
    REGION_PROVINCES.findIndex((province) => province.name === provinceName),
  )
  const province = REGION_PROVINCES[provinceIndex]
  const cityIndex = Math.max(
    0,
    province.cities.findIndex((city) => city.name === cityName),
  )
  const city = province.cities[cityIndex]
  const districtIndex = Math.max(0, city.districts.findIndex((district) => district === districtName))
  return [provinceIndex, cityIndex, districtIndex]
}

/** 严格匹配：三项都能在精简表里找到才算命中，用于避免把未收录地区回填成第一个省。 */
export function matchesRegion(
  provinceName: string | null,
  cityName: string | null,
  districtName: string | null,
): boolean {
  const province = REGION_PROVINCES.find((item) => item.name === provinceName)
  if (!province) {
    return false
  }
  const city = province.cities.find((item) => item.name === cityName)
  if (!city) {
    return false
  }
  return city.districts.includes(districtName || '')
}
