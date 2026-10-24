package com.soloeats.delivery.service;

/**
 * 配送超时扫描：状态 40 配送中、承诺送达时间已过、未妥投的单据，
 * 每个配送单只写一条 type=1 的超时异常记录。
 */
public interface DeliveryTimeoutService {

    /** 返回本轮新写入的异常条数。 */
    int markTimeouts();
}
