package com.ticketmind.service.todo;

import com.ticketmind.model.dto.TodoListArchiveEvent;

public interface TodoListArchivePublisher {

    void publish(TodoListArchiveEvent event);
}
