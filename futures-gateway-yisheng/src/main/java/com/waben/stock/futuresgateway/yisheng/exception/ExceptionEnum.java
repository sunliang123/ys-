package com.waben.stock.futuresgateway.yisheng.exception;

public enum ExceptionEnum {

	Unknow("1000", "未知异常"),
	
	Client_NotConnected("1001", "API连接异常!"),

	Contract_NotSupported("1002", "不支持的合约类型!"),

	Action_NotSupported("1003", "不支持的交易方向!"),

	OrderType_NotSupported("1004", "不支持的订单类型!"),

	Order_NotExist("1005", "订单不存在!"),

	PartFilled_CannotCancel("1006", "订单已部分成交不能被取消!"),
	
	CurrentStatus_CannotCancel("1007", "当前订单状态不能被取消!");

	private ExceptionEnum(String code, String errorMsg) {
		this.code = code;
		this.errorMsg = errorMsg;
	}

	private String code;

	private String errorMsg;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

}
