package com.soloeats.service.service.impl;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.soloeats.service.domain.entity.QuickReply;
import com.soloeats.service.mapper.QuickReplyMapper;
import com.soloeats.service.service.command.QuickReplyCommand;
import com.soloeats.service.service.view.QuickReplyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickReplyServiceImplTest {

    private static final long REPLY_ID = 12L;

    @Mock
    private QuickReplyMapper quickReplyMapper;

    private QuickReplyServiceImpl quickReplyService;

    @BeforeEach
    void setUp() {
        quickReplyService = new QuickReplyServiceImpl(quickReplyMapper);
    }

    @Test
    @DisplayName("新增：分类与内容去空白、换行归一，sort/status 缺省为 0")
    void createNormalizesText() {
        when(quickReplyMapper.insert(any(QuickReply.class))).thenAnswer(invocation -> {
            QuickReply inserted = invocation.getArgument(0);
            inserted.setId(REPLY_ID);
            return 1;
        });

        Long id = quickReplyService.create(
                new QuickReplyCommand("  订单咨询  ", "  稍等，我查一下\r\n ", null, null));

        assertThat(id).isEqualTo(REPLY_ID);
        ArgumentCaptor<QuickReply> captor = ArgumentCaptor.forClass(QuickReply.class);
        verify(quickReplyMapper).insert(captor.capture());
        QuickReply saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo("订单咨询");
        assertThat(saved.getContent()).isEqualTo("稍等，我查一下");
        assertThat(saved.getSort()).isZero();
        assertThat(saved.getStatus()).isZero();
    }

    @Test
    @DisplayName("修改：命中记录后写入新分类、内容、排序与状态")
    void updateAppliesCommand() {
        QuickReply existing = reply();
        when(quickReplyMapper.selectById(REPLY_ID)).thenReturn(existing);

        quickReplyService.update(REPLY_ID, new QuickReplyCommand("配送", "骑手马上就到", 2, 1));

        verify(quickReplyMapper).updateById(existing);
        assertThat(existing.getCategory()).isEqualTo("配送");
        assertThat(existing.getContent()).isEqualTo("骑手马上就到");
        assertThat(existing.getSort()).isEqualTo(2);
        assertThat(existing.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("查询：按分类返回话术视图")
    void listReturnsViews() {
        when(quickReplyMapper.selectList(any())).thenReturn(List.of(reply()));

        List<QuickReplyView> views = quickReplyService.list("订单");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(REPLY_ID);
        assertThat(views.get(0).content()).isEqualTo("稍等一下");
        verify(quickReplyMapper).selectList(any());
    }

    @Test
    @DisplayName("删除：deleteById 由 @TableLogic 改写为 deleted=1")
    void deleteUsesLogicalDelete() throws Exception {
        when(quickReplyMapper.selectById(REPLY_ID)).thenReturn(reply());
        when(quickReplyMapper.deleteById(REPLY_ID)).thenReturn(1);

        quickReplyService.delete(REPLY_ID);

        verify(quickReplyMapper).deleteById(REPLY_ID);
        assertThat(QuickReply.class.getDeclaredField("deleted").getAnnotation(TableLogic.class))
                .as("deleted 字段必须带 @TableLogic，否则删除会变成物理删除")
                .isNotNull();
    }

    @Test
    @DisplayName("逻辑删除后不再出现在列表中")
    void deletedQuickReplyNotInList() {
        when(quickReplyMapper.selectById(REPLY_ID)).thenReturn(reply());
        when(quickReplyMapper.deleteById(REPLY_ID)).thenReturn(1);
        // @TableLogic 让列表查询自动追加 deleted=0，已删除行不会被 selectList 返回
        when(quickReplyMapper.selectList(any())).thenReturn(List.of());

        quickReplyService.delete(REPLY_ID);

        assertThat(quickReplyService.list(null)).isEmpty();
    }

    private QuickReply reply() {
        QuickReply reply = new QuickReply();
        reply.setId(REPLY_ID);
        reply.setCategory("订单");
        reply.setContent("稍等一下");
        reply.setSort(1);
        reply.setStatus(0);
        reply.setDeleted(0);
        return reply;
    }
}
