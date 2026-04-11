CC = gcc
CFLAGS = -Wall -g -pthread -I./include -I../common/include

ifeq ($(DEBUG), 1)
    CFLAGS += -DDEBUG
endif

ifdef REQUEST_LOSS
    CFLAGS += -DREQUEST_LOSS=$(REQUEST_LOSS)
endif

ifdef REPLY_LOSS
    CFLAGS += -DREPLY_LOSS=$(REPLY_LOSS)
endif

BUILD_DIR = build
COMMON_DIR = ../common
COMMON_OBJ = ../common/build/protocol.o
TARGET = $(BUILD_DIR)/server_app
OBJS = $(BUILD_DIR)/server_app.o $(BUILD_DIR)/server.o $(COMMON_OBJ)
HEADERS = ./include/server.h ../common/include

all: $(BUILD_DIR) common $(TARGET)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

.PHONY: common
common:
	$(MAKE) -C $(COMMON_DIR)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJS)

$(BUILD_DIR)/%.o: src/%.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(TARGET) $(OBJS)

clean-all: clean
	rm -f $(BUILD_DIR)/*.txt
	$(MAKE) -C $(COMMON_DIR) clean