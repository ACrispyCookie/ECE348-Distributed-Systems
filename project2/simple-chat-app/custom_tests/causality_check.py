import re
import sys

# We added parenthesis (...) to capture the Sender (Group 1) and the Payload (Group 2)
PATTERN = re.compile(r'\[GROUP .+\] At .+ (\S+) said: (.+)')

def extract_and_parse_messages(filepath):
    messages = []
    parsed_data = [] # Stores tuples of (sender, sequence_number, full_line)
    
    with open(filepath) as f:
        for line in f:
            match = PATTERN.search(line)
            if match:
                full_text = match.group(0)
                sender = match.group(1)
                payload = match.group(2)
                
                # Extract the sequence number from the end of the payload (e.g., "Client-0-5" -> 5)
                try:
                    seq_num = int(payload.split('-')[-1])
                except ValueError:
                    seq_num = -1
                    
                messages.append(full_text)
                parsed_data.append((sender, seq_num, full_text))
                
    return messages, parsed_data

def check_causality(parsed_data, filename):
    expected_seq = {}
    
    for sender, seq_num, full_text in parsed_data:
        if seq_num == -1:
            continue
            
        expected = expected_seq.get(sender, 0)
        
        if seq_num != expected:
            print(f"CAUSALITY VIOLATION in {filename}")
            print(f"  Sender: {sender}")
            print(f"  Expected sequence: {expected}")
            print(f"  Actually got: {seq_num}")
            print(f"  Message: {full_text}")
            return False
        
        expected_seq[sender] = expected + 1
        
    print(f"SUCCESS: Causal Order (FIFO per sender) is verified for {filename}!")
    return True

def main():
    if len(sys.argv) != 3:
        print("Usage: python order_check.py <file1> <file2>")
        sys.exit(1)

    file1, file2 = sys.argv[1], sys.argv[2]
    msgs1, parsed1 = extract_and_parse_messages(file1)
    msgs2, parsed2 = extract_and_parse_messages(file2)

    print(f"File 1 message count: {len(msgs1)}")
    print(f"File 2 message count: {len(msgs2)}")
    print("-" * 50)

    total_order_ok = False
    if msgs1 == msgs2:
        print("SUCCESS: Files contain the same messages in the same order. (Total Order Verified)")
        total_order_ok = True
    else:
        print("FAIL: Files differ! (Total Order Violated)")
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

    print("-" * 50)

    if total_order_ok:
        check_causality(parsed1, "Both Files")
    else:
        check_causality(parsed1, file1)
        check_causality(parsed2, file2)

if __name__ == '__main__':
    main()
