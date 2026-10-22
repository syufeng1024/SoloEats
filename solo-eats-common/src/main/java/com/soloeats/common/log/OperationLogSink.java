package com.soloeats.common.log;

/**
 * 操作日志持久化端口。common 不依赖业务模块，由 system 模块实现并注册为 Bean。
 *
 * <p>实现方必须保证异步执行且失败只告警，不得把异常抛回业务线程。
 */
public interface OperationLogSink {

    void persist(OperationLogRecord record);
}
