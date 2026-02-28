CC = gcc
CFLAGS = -Wall -g -pthread

ifeq ($(DEBUG), 1)
    CFLAGS += -DDEBUG
endif

TARGET = server_app
OBJS = server_app.o server_api.o
HEADERS = server_api.h internal_server.h

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJS)

%.o: %.c $(HEADERS)
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)
