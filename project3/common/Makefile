CC = gcc
CFLAGS = -Wall -g -pthread -I./include

BUILD_DIR = build
OBJ = $(BUILD_DIR)/protocol.o

all: $(BUILD_DIR) $(OBJ)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(BUILD_DIR)/protocol.o: src/protocol.c include/protocol.h
	$(CC) $(CFLAGS) -c src/protocol.c -o $(OBJ)

clean:
	rm -f $(OBJ)