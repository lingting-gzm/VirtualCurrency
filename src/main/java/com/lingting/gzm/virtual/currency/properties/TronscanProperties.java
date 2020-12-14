package com.lingting.gzm.virtual.currency.properties;

import com.lingting.gzm.virtual.currency.endpoints.Endpoints;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * tronscan 平台配置
 *
 * @author lingting 2020-09-01 16:53
 */
@Data
@Accessors(chain = true)
public class TronscanProperties implements PlatformProperties {

	/**
	 * 节点
	 */
	private Endpoints endpoints;

}
