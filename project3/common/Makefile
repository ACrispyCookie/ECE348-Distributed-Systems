CC = gcc
CFLAGS = -Wall -g -pthread -I./include

BUILD_DIR = build
OBJ = $(BUILD_DIR)/protocol.o
TEST_OBJ = $(BUILD_DIR)/test.o
TEST_BIN = $(BUILD_DIR)/test

all: $(BUILD_DIR) $(OBJ)

test: $(BUILD_DIR) $(TEST_BIN)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/protocol.o: src/protocol.c include/protocol.h
	$(CC) $(CFLAGS) -c src/protocol.c -o $(OBJ)

$(TEST_OBJ): tests/test.c include/protocol.h
	$(CC) $(CFLAGS) -c tests/test.c -o $(TEST_OBJ)

$(TEST_BIN): $(OBJ) $(TEST_OBJ)
	$(CC) $(CFLAGS) -o $(TEST_BIN) $(OBJ) $(TEST_OBJ)

clean:
	rm -rf $(BUILD_DIR)