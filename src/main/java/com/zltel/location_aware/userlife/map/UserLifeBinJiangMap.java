package com.zltel.location_aware.userlife.map;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.zltel.common.utils.string.StringUtil;
import com.zltel.location_aware.userlife.bean.Pointer;
import com.zltel.location_aware.userlife.main.UserLifeBinJiangMain;

public class UserLifeBinJiangMap extends Mapper<LongWritable, Text, Text, Text> {
	private static Logger logout = LoggerFactory.getLogger(UserLifeBinJiangMap.class);

	private int startRegion = 0;
	private int endRegion = 99;

	@Override
	protected void setup(Mapper<LongWritable, Text, Text, Text>.Context context)
			throws IOException, InterruptedException {

		Configuration config = context.getConfiguration();
		String ssr = config.get(UserLifeBinJiangMain.STR_STARTREGION);
		String esr = config.get(UserLifeBinJiangMain.STR_ENDREGION);
		if (StringUtil.isNotNullAndEmpty(ssr) && StringUtil.isNum(ssr)) {
			startRegion = Integer.valueOf(ssr);
		}
		if (StringUtil.isNotNullAndEmpty(esr) && StringUtil.isNum(esr)) {
			endRegion = Integer.valueOf(esr);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hadoop.mapreduce.Mapper#map(java.lang.Object,
	 * java.lang.Object, org.apache.hadoop.mapreduce.Mapper.Context)
	 */
	@Override
	protected void map(LongWritable _key, Text value, Mapper<LongWritable, Text, Text, Text>.Context context)
			throws IOException, InterruptedException {
		// logout.info("start Map -----------------------------");

		// stime,etime,ci,lac,imsi,source,nettype

		String[] strs = value.toString().trim().split("\\t");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		// SimpleDateFormat sdfd = new SimpleDateFormat("yyyyMMdd");
		String[] colum = null;
		if (strs.length > 1) {
			String ci = null;
			String rat = null;
			String type = null; // 网络类型
			String lac = null;
			String source = null;
			String stime = null;
			String etime = null;
			String imsi = null;

			String[] ss = strs[0].trim().split("_");
			if (ss.length != 2) {
				return;
			}
			colum = (strs[1].trim() + "1").split("\\|");
			if (colum.length >= 39) {
				// GN
				source = Pointer.SOURCE_GN;
				ci = Integer.parseInt("".equals(colum[11]) ? "0" : colum[11], 16) + "";

				rat = colum[7];
				if ("6".equals(rat)) {
					type = "4";
				} else if ("1".equals(rat)) {
					type = "2";
				} else if ("2".equals(rat)) {
					type = "3";
				}
				stime = sdf.format(new Date(Long.parseLong(colum[0]) * 1000));
				etime = sdf.format(new Date(Long.parseLong(colum[1]) * 1000));
				imsi = colum[4];
				rat = colum[7];
				lac = Integer.parseInt("".equals(colum[10]) ? "0" : colum[10], 16) + "";

				// 16 进制 -> 10进制
				if ("1".equals(rat)) {// 3G
					ci = Integer.parseInt("".equals(colum[12]) ? "0" : colum[12], 16) + "";
				} else if ("2".equals(rat)) {// 2G
					ci = Integer.parseInt("".equals(colum[11]) ? "0" : colum[11], 16) + "";
				}

				// 4g网络
				if ("4".equals(type)) {
					lac = Integer.parseInt("".equals(colum[colum.length - 3]) ? "0" : colum[colum.length - 3], 16) + "";
					ci = Integer.parseInt("".equals(colum[colum.length - 2]) ? "0" : colum[colum.length - 2], 16) + "";
				}

			} else {
				// CS
				source = Pointer.SOURCE_CS;
				colum = strs[1].trim().split(",");
				if (ss[0].length() > 0 && colum.length > 15) {
					type = "2";// 2g
					imsi = colum[1];
					stime = colum[6];
					etime = colum[7];
					lac = colum[8];
					ci = colum[9];
				}
			}

			// 判断是否符合
			if (!checkIMSI(imsi)) {
				return;
			}

			// 创建 pointer
			Pointer point = new Pointer();
			point.setImsi(imsi);
			point.setSource(source);
			point.setNettype(type);
			if (StringUtil.isNotNullAndEmpty(stime) && StringUtil.isNum(stime)) {
				point.setStime(stime);
			}
			if (StringUtil.isNotNullAndEmpty(etime) && StringUtil.isNum(etime)) {
				point.setEtime(etime);
			}
			if (StringUtil.isNotNullAndEmpty(ci)) {
				point.setCi(ci);
			}
			if (StringUtil.isNotNullAndEmpty(lac)) {
				point.setLac(lac);
			}
			if (point.avaliable() && point._stime() != null && point._etime() != null) {
				String json = JSON.toJSONString(point);
				context.write(new Text(imsi), new Text(json));
				// logout.info("imsi:" + imsi + "\njson:" + json);
			} else {
				logout.warn(" 数据不完整! imsi:" + imsi + " 数据:" + point);
			}
		} else {
			logout.warn(" 数据有误: " + value.toString());
		}

	}

	/**
	 * 检测 IMSI 是否符合规则
	 * 
	 * @param imsi
	 * @return true: 符合，false:不符合
	 */
	private boolean checkIMSI(String imsi) {
		if (StringUtil.isNullOrEmpty(imsi)) {
			return false;
		}
		String cl = imsi.substring(imsi.length() - 2);
		if (StringUtil.isNum(cl)) {
			int _cl = Integer.valueOf(cl);
			if (startRegion == endRegion) {
				return startRegion == _cl;
			} else {
				return startRegion < _cl && _cl <= endRegion;
			}
		}
		return false;
	}

	public static void main(String[] args) {
		UserLifeBinJiangMap ulbjm = new UserLifeBinJiangMap();
		boolean f = ulbjm.checkIMSI("13434515413434");
		System.out.println(f);
	}
}