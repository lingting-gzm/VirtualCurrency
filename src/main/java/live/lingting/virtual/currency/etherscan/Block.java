package live.lingting.virtual.currency.etherscan;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import live.lingting.virtual.currency.core.JsonRpcClient;

/**
 * @author lingting 2021/1/5 20:03
 */
@NoArgsConstructor
@Getter
@Setter
public class Block extends BaseResponse {

	@JsonProperty("difficulty")
	private String difficulty;

	@JsonProperty("extraData")
	private String extraData;

	@JsonProperty("gasLimit")
	private String gasLimit;

	@JsonProperty("gasUsed")
	private String gasUsed;

	@JsonProperty("hash")
	private String hash;

	@JsonProperty("logsBloom")
	private String logsBloom;

	@JsonProperty("miner")
	private String miner;

	@JsonProperty("mixHash")
	private String mixHash;

	@JsonProperty("nonce")
	private String nonce;

	@JsonProperty("number")
	private String number;

	@JsonProperty("parentHash")
	private String parentHash;

	@JsonProperty("receiptsRoot")
	private String receiptsRoot;

	@JsonProperty("sha3Uncles")
	private String sha3Uncles;

	@JsonProperty("size")
	private String size;

	@JsonProperty("stateRoot")
	private String stateRoot;

	@JsonProperty("timestamp")
	private String timestamp;

	@JsonProperty("totalDifficulty")
	private String totalDifficulty;

	@JsonProperty("transactionsRoot")
	private String transactionsRoot;

	@JsonProperty("transactions")
	private List<String> transactions;

	@JsonProperty("uncles")
	private List<Object> uncles;

	public static Block of(JsonRpcClient client, BlockEnum block) throws Throwable {
		return client.invoke("eth_getBlockByNumber", Block.class, block.getVal(), false);
	}

	public static Block of(JsonRpcClient client, String hash) throws Throwable {
		return client.invoke("eth_getBlockByHash", Block.class, hash, false);
	}

}
