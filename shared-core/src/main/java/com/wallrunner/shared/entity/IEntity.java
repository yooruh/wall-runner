package com.wallrunner.shared.entity;

import java.io.Serializable;

/**
 * 实体标记接口。
 * 
 * UML 建模意义：所有游戏实体均实现此接口，便于类图中展示泛化关系。
 * 设计原则：依赖倒置（DIP）— 上层模块依赖抽象而非具体实现。
 */
public interface IEntity extends Serializable {
    String getId();
    void setId(String id);
}
