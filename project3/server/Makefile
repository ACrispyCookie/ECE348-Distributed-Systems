CC = gcc
CFLAGS = -Wall -g -pthread -I./include

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
TARGET = $(BUILD_DIR)/server_app
OBJS = $(BUILD_DIR)/server_app.o $(BUILD_DIR)/server.o
HEADERS = ./include/server.h

all: $(BUILD_DIR) $(TARGET)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJS)

$(BUILD_DIR)/%.o: src/%.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(TARGET) $(OBJS)

clean-all: clean
	rm -f $(BUILD_DIR)/*.txt