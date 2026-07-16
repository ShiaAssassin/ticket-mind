package com.ticketmind.service.todo;

import com.ticketmind.model.dto.TodoListArchiveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ticket-mind.rabbitmq", name = "enabled", havingValue = "true")
public class TodoListArchiveListener {

    private final TodoListArchiveStorage todoListArchiveStorage;

    @RabbitListener(queues = "${ticket-mind.rabbitmq.todo-list-archive-queue}")
    public void onTodoListCompleted(TodoListArchiveEvent event) {
        todoListArchiveStorage.store(event);
        log.info("Archived completed TodoList, taskId={}, itemCount={}", event.taskId(), event.items().size());
    }
}
