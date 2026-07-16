package com.ticketmind.service.todo;

import com.ticketmind.model.dto.TodoListArchiveEvent;

public interface TodoListArchiveStorage {

    void store(TodoListArchiveEvent event);
}
