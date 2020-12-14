package com.lingting.gzm.virtual.currency.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.lingting.gzm.virtual.currency.contract.Contract;
import com.lingting.gzm.virtual.currency.contract.Etherscan;
import com.lingting.gzm.virtual.currency.enums.EtherscanReceiptStatus;
import com.lingting.gzm.virtual.currency.enums.TransactionStatus;
import com.lingting.gzm.virtual.currency.enums.VcPlatform;
import com.lingting.gzm.virtual.currency.etherscan.EtherscanUtil;
import com.lingting.gzm.virtual.currency.properties.InfuraProperties;
import com.lingting.gzm.virtual.currency.service.VirtualCurrencyService;
import com.lingting.gzm.virtual.currency.VirtualCurrencyTransaction;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * @author lingting 2020-09-01 17:16
 */
@Slf4j
public class InfuraServiceImpl implements VirtualCurrencyService {

	private static final String INPUT_EMPTY = "0x";

	@Getter
	private final Web3j web3j;

	private final InfuraProperties properties;

	private static final String EMPTY_ADDRESS = "0x0000000000000000000000000000000000000000";

	@Getter
	private static final Map<Contract, Integer> CONTRACT_DECIMAL_CACHE;

	static {
		CONTRACT_DECIMAL_CACHE = new ConcurrentHashMap<>(Etherscan.values().length + 1);
		CONTRACT_DECIMAL_CACHE.put(Etherscan.ETH, 18);
	}

	public InfuraServiceImpl(InfuraProperties properties) {
		this.properties = properties;
		// 使用web3j连接infura客户端
		web3j = Web3j.build(properties.getHttpService());
	}

	@Override
	@SneakyThrows
	public Optional<VirtualCurrencyTransaction> getTransactionByHash(String hash) {
		EthTransaction ethTransaction = web3j.ethGetTransactionByHash(hash).send();

		Optional<Transaction> optional;
		// 订单出错
		if (ethTransaction.hasError()) {
			log.error("查询eth订单出错: code: {}, message:{}", ethTransaction.getError().getCode(),
					ethTransaction.getError().getMessage());
			optional = Optional.empty();
		}
		// 订单没出错
		else {
			optional = ethTransaction.getTransaction();
		}

		/*
		 * 订单信息为空 如果交易还没有被打包，就查询不到交易信息
		 */
		if (!optional.isPresent()) {
			return Optional.empty();
		}
		Transaction transaction = optional.get();

		// 获取合约代币
		Etherscan contract = Etherscan.getByHash(transaction.getTo());
		// 合约地址
		String contractAddress = contract == null ? StrUtil.EMPTY : contract.getHash();
		// 解析input数据
		EtherscanUtil.Input input;
		// 不是使用代币交易，而是直接使用eth交易
		if (INPUT_EMPTY.equals(transaction.getInput())) {
			input = new EtherscanUtil.Input().setTo(transaction.getTo())
					.setValue(new BigDecimal(transaction.getValue())).setContract(Etherscan.ETH);
		}
		else {
			input = EtherscanUtil.resolveInput(transaction.getInput());
		}

		if (input.getContract() != null) {
			contract = input.getContract();
			contractAddress = contract.getHash();
		}
		VirtualCurrencyTransaction virtualCurrencyTransaction = new VirtualCurrencyTransaction()

				.setVcPlatform(VcPlatform.ETHERSCAN)

				.setBlock(transaction.getBlockNumber()).setHash(transaction.getHash()).setFrom(transaction.getFrom())

				.setTo(input.getTo())
				// 设置合约类型, input 中的优先
				.setContract(contract)
				// 设置合约地址
				.setContractAddress(contractAddress)
				// 设置金额
				.setValue(getNumberByBalanceAndContract(input.getValue(), contract))
				// 设置 input data
				.setInput(input);

		// 获取交易状态
		Optional<TransactionReceipt> receiptOptional = web3j.ethGetTransactionReceipt(hash).send()
				.getTransactionReceipt();
		if (receiptOptional.isPresent()
				&& receiptOptional.get().getStatus().equals(EtherscanReceiptStatus.SUCCESS.getValue())) {
			// 交易成功
			virtualCurrencyTransaction.setStatus(TransactionStatus.SUCCESS);
		}
		else {
			virtualCurrencyTransaction.setStatus(TransactionStatus.FAIL);
		}

		// 获取交易时间
		EthBlock block = web3j.ethGetBlockByHash(transaction.getBlockHash(), false).send();

		// 从平台获取的交易是属于 UTC 时区的
		return Optional.of(virtualCurrencyTransaction.setTime(
				LocalDateTime.ofEpochSecond(Convert.toLong(block.getBlock().getTimestamp()), 0, ZoneOffset.UTC)));
	}

	@Override
	@SuppressWarnings("all")
	public Integer getDecimalsByContract(Contract contract) {
		if (contract == null) {
			return 0;
		}
		// 缓存合约精度, 且已存在指定合约的精度缓存
		if (CONTRACT_DECIMAL_CACHE.containsKey(contract)) {
			return CONTRACT_DECIMAL_CACHE.get(contract);
		}

		Integer decimals = 0;

		List<Type> types = ethCall("decimals", new ArrayList<>(0), ListUtil.toList(new TypeReference<Uint8>() {
		}), EMPTY_ADDRESS, contract.getHash());
		// 返回值不为空
		if (!CollectionUtil.isEmpty(types)) {
			decimals = Convert.toInt(types.get(0).getValue().toString(), 0);
		}
		// 缓存合约精度 CONTRACT_DECIMAL_CACHE.put(contract, decimals);
		return decimals;
	}

	@Override
	@SneakyThrows
	@SuppressWarnings("all")
	public BigDecimal getBalanceByAddressAndContract(String address, Contract contract) {
		if (contract == Etherscan.ETH) {
			return new BigDecimal(web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance());
		}
		// 执行方法
		List<Type> list = ethCall("balanceOf", ListUtil.toList(new Address(address)),
				ListUtil.toList(new TypeReference<Uint256>() {
				}), address, contract.getHash());
		// 返回值不为空
		if (!CollectionUtil.isEmpty(list)) {
			return new BigDecimal(list.get(0).getValue().toString());
		}
		return BigDecimal.ZERO;
	}

	@Override
	public BigDecimal getNumberByBalanceAndContract(BigDecimal balance, Contract contract, MathContext mathContext) {
		// 合约为null 返回原值
		if (contract == null) {
			return balance;
		}
		// 计算返回值
		return balance.divide(BigDecimal.TEN.pow(getDecimalsByContract(contract)), mathContext);
	}

	/**
	 * 创建交易
	 * @param method 执行方法名
	 * @param input 输入参数
	 * @param out 输出参数
	 * @param from 从
	 * @param to 去
	 * @return java.util.List
	 * @author lingting 2020-12-11 16:21
	 */
	@SuppressWarnings("all")
	private List<Type> ethCall(String method, List<Type> input, List<TypeReference<?>> out, String from, String to) {
		return ethCall(method, input, out, from, to, DefaultBlockParameterName.LATEST);
	}

	@SneakyThrows
	@SuppressWarnings("all")
	private List<Type> ethCall(String method, List<Type> input, List<TypeReference<?>> out, String from, String to,
			DefaultBlockParameter block) {
		Assert.notNull(input);
		Assert.notNull(out);
		// 编译方法
		Function function = new Function(method, input, out);
		// 编码
		String data = FunctionEncoder.encode(function);
		// 创建交易
		org.web3j.protocol.core.methods.request.Transaction transaction = org.web3j.protocol.core.methods.request.Transaction
				.createEthCallTransaction(from, to, data);
		// 执行
		EthCall call = web3j.ethCall(transaction, block).send();
		// 解析返回值
		return FunctionReturnDecoder.decode(call.getValue(), function.getOutputParameters());
	}

}
