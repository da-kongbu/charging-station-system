package com.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 知识块数据结构
 * 每个知识块对应一段充电桩使用说明或计费规则的文本，以及该文本的向量表示
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeChunk {

    /**
     * 知识块的原始文本内容
     */
    private String text;

    /**
     * 该文本经 Embedding 模型转化后的向量数组
     * 用于后续与用户提问向量做余弦相似度计算
     */
    private float[] embedding;
}
