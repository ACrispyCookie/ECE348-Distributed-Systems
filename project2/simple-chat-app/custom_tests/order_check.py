import re
import sys

PATTERN = re.compile(r'\[GROUP .+\] At .+ \w+ said: .+')

def extract_messages(filepath):
    messages = []
    with open(filepath) as f:
        for line in f:
            match = PATTERN.search(line)
            if match:
                messages.append(match.group())
    return messages

def main():
    if len(sys.argv) != 3:
        print("Usage: python order_check.py <file1> <file2>")
        sys.exit(1)

    file1, file2 = sys.argv[1], sys.argv[2]
    msgs1 = extract_messages(file1)
    msgs2 = extract_messages(file2)

    print("File 1 message count: " + str(len(msgs1)))
    print("File 2 message count: " + str(len(msgs2)))

    if msgs1 == msgs2:
        print("SUCCESS: Files contain the same messages in the same order.")
    else:
        print("FAIL: Files differ!")
        if len(msgs1) != len(msgs2):
            print(f"  {file1}: {len(msgs1)} messages")
            print(f"  {file2}: {len(msgs2)} messages")
        else:
            for i, (m1, m2) in enumerate(zip(msgs1, msgs2)):
                if m1 != m2:
                    print(f"  First difference at message {i + 1}:")
                    print(f"    {file1}: {m1}")
                    print(f"    {file2}: {m2}")
                    break

if __name__ == '__main__':
    main()
