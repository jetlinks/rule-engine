# 规则引擎

基于流程的流式规则引擎。

[![Maven Central](https://img.shields.io/maven-central/v/org.jetlinks/rule-engine.svg)](http://search.maven.org/#search%7Cga%7C1%7Crule-engine)
[![Maven metadata URL](https://img.shields.io/maven-metadata/v/https/oss.sonatype.org/content/repositories/snapshots/org/jetlinks/rule-engine/maven-metadata.xml.svg)](https://oss.sonatype.org/content/repositories/snapshots/org/jetlinks/rule-engine)
[![Build Status](https://travis-ci.com/jetlinks/rule-engine.svg?branch=master)](https://travis-ci.com/jetlinks/rule-engine)
[![codecov](https://codecov.io/gh/jetlinks/rule-engine/branch/master/graph/badge.svg)](https://codecov.io/gh/jetlinks/rule-engine)

# 任务执行器扩展

AbstractTaskExecutor 提供可选执行观测、稳定 observationSpec 和输入上下文恢复；默认 execute 与函数节点共用 observe 边界。迁移节点开启 observeExecution，按需通过 tagObservation 追加标签；Recorder 生命周期、noop、父作用域和取消复用 core Observations。旧手工节点默认关闭公共观测，避免双记。Spec 和开关在组装时读取，业务仍在每次订阅内执行；启用路径复用 Observations 的惰性边界，禁用路径保留一次 defer。

FunctionTaskExecutor 负责直接 execute 与订阅输入的函数执行和结果分发，applyAsync 默认直接适配 apply 返回流。平台通过 prepareContext 在观测选择前恢复输入，通过 applyAsync 写出输出信封；两者都不额外订阅业务或引入本体依赖。原结果事件、tracer 与 onError 继续由函数执行器负责。

# 运行指标

默认任务在 `Task.dump().executionMetrics` 中返回当前任务生命周期内的执行次数、失败次数和最近开始执行时间。统计独立于 Recorder 与执行日志开关，仅保存在任务上下文内；热重载保留，任务销毁或重建后重新计数，不用于快照持久化或恢复。通过 `RuleEngine.getTasks(instanceId).flatMap(Task::dump)` 可沿现有调度 RPC 获取各节点的最新值，不需要另建指标注册或查询服务。

直接调用和 Input 的单条处理回调由引擎观察；定时、订阅等自主产生数据的执行器，应在每次有限分发上使用 `context.observeExecution(mono)`，不能包装常驻数据流。同步业务调用需通过 `Mono.defer` 延迟到订阅中，异常恢复应位于观察点之后，或通过 `context.onError` 报告。嵌套观察同一任务只计一次，不按输出数量累计，空完成也计数，取消不计失败；自定义 `ExecutionContext` 默认透传，自定义 `Task` 的指标可为空。

# 规则模型

```text
//规则模型
RuleModel{ 
    events:[ RuleLink ]     # 事件连接点,用于自定义规则事件的处理规则
    nodes:[ RuleNodeModel ] # 所有节点信息，包含事件节点
}
//节点模型
RuleNodeModel{
    executor: ""            # 节点执行器标识
    configuration: { Map }  # 节点配置
    events:[ RuleLink ]     # 事件连接点,用于自定义节点事件的处理规则
    inputs:[ RuleLink ]     # 输入连接点
    outputs:[ RuleLink ]    # 输出连接点
}
//连接点，将2个规则节点关联
RuleLink{
    type: ""                # 类型，为事件连接点时类型则为事件类型
    condition: Condition    # 连接条件
    source: RuleNodeModel   # 连接节点
    target: RuleNodeModel   # 被连接节点
}
//条件
Condition{
    type: ""                # 条件类型。如: expression
    configuration: { Map }  # 条件配置
}
```
