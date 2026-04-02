import os
import re
import sys
import glob
import matplotlib.pyplot as plt

def parse_benchmark_files(directory):
    """
    Scans the directory for files matching 'prefixN_I' (e.g., single3_1, concurrent3_1).
    Extracts ALL throughput measurements found inside each file.
    Returns: { 'single': { 2: [tp1, tp2], 3: [...] }, 'concurrent': { ... } }
    """
    data = {}
    
    # Matches files like single1_1, concurrent3_2.txt, etc.
    # Group 1 = mode (single/concurrent), Group 2 = group size (N)
    filename_pattern = re.compile(r'([a-zA-Z]+)(\d+)(?:_\d+)?')
    throughput_pattern = re.compile(r'Throughput:\s+([\d\.]+)\s+msg/sec')

    for filepath in glob.glob(os.path.join(directory, '*')):
        if not os.path.isfile(filepath):
            continue
            
        filename = os.path.basename(filepath)
        match = filename_pattern.search(filename)
        
        if match:
            mode = match.group(1).lower()
            group_size = int(match.group(2))
            
            if mode not in data:
                data[mode] = {}
            if group_size not in data[mode]:
                data[mode][group_size] = []
            
            with open(filepath, 'r', encoding='utf-8', errors='ignore') as file:
                for line in file:
                    tp_match = throughput_pattern.search(line)
                    if tp_match:
                        throughput = float(tp_match.group(1))
                        # Append value to global pool across all files
                        data[mode][group_size].append(throughput)

    return data

def calculate_trimmed_averages(data):
    """
    Calculates the average for each group size, discarding the highest and lowest values globally.
    """
    averages = {}
    print("--- Data Processing ---")
    
    for mode, sizes_dict in data.items():
        averages[mode] = {}
        for size in sorted(sizes_dict.keys()):
            values = sizes_dict[size]
            if not values:
                continue
                
            if len(values) >= 3:
                # Sort and slice off the first (lowest) and last (highest) elements globally
                sorted_vals = sorted(values)
                trimmed = sorted_vals[1:-1]
                avg = sum(trimmed) / len(trimmed)
                print(f"[{mode.upper()}] Group Size {size}: {len(values)} total runs. Dropped {sorted_vals[0]:.2f} & {sorted_vals[-1]:.2f}. Trimmed Avg -> {avg:.2f} msg/sec")
            elif len(values) > 0:
                # If there are only 1 or 2 runs total, we can't safely drop the extremes
                avg = sum(values) / len(values)
                print(f"[{mode.upper()}] Group Size {size}: Only {len(values)} total runs. No drops. Avg -> {avg:.2f} msg/sec")
                
            averages[mode][size] = avg
            
    return averages

def plot_graphs(averages):
    """
    Generates SEPARATE line plots for each mode (single, concurrent).
    """
    if not averages:
        print("Error: No data to plot!")
        return

    # Styling for different lines
    colors = {'single': '#007acc', 'concurrent': '#d9534f', 'default': '#5cb85c'}
    markers = {'single': 'o', 'concurrent': 's', 'default': '^'}
    
    # Loop through each mode and create a separate figure
    for mode, sizes_dict in averages.items():
        if not sizes_dict:
            continue
            
        plt.figure(figsize=(10, 6))
            
        sizes = sorted(sizes_dict.keys())
        throughputs = [sizes_dict[size] for size in sizes]
        max_throughput = max(throughputs) if throughputs else 0
        
        color = colors.get(mode, colors['default'])
        marker = markers.get(mode, markers['default'])
        label_name = f"{mode.capitalize()} Sender(s)"
        
        plt.plot(sizes, throughputs, marker=marker, linestyle='-', color=color, linewidth=2, markersize=8, label=label_name)
        
        # Add value labels slightly above each point
        for x, y in zip(sizes, throughputs):
            offset = max_throughput * 0.03 if max_throughput > 0 else 100
            plt.text(x, y + offset, f"{y:.0f}", ha='center', va='bottom', fontsize=10, color=color)

        # --- CHANGED: Dynamic title exactly as requested ---
        if mode == 'single':
            title_text = 'Group communication throughput single sender'
        else:
            title_text = 'Group communication throughput multiple senders'
            
        plt.title(title_text, fontsize=14, fontweight='bold')
        # ---------------------------------------------------
        
        plt.xlabel('Group Size (N)', fontsize=12)
        plt.ylabel('Average Throughput (msg/sec)', fontsize=12)
        
        plt.xticks(sizes) 
        
        # Add 15% headroom to the top of the graph
        plt.ylim(0, max_throughput * 1.15 if max_throughput > 0 else 100) 
        
        plt.grid(True, linestyle='--', alpha=0.7)
        plt.legend()
        
        # Methodology info text at the bottom
        info_text = "* Data points represent the average of all iterations. Global min and max outliers are discarded if ≥3 total runs."
        plt.figtext(0.5, 0.02, info_text, wrap=True, horizontalalignment='center', 
                    fontsize=10, fontstyle='italic', color='dimgray')
        
        # Adjust the layout to leave blank space at the bottom for the text
        plt.tight_layout(rect=[0, 0.06, 1, 1])
        
        # Save dynamically named file
        filename = f'throughput_benchmark_{mode}.png'
        plt.savefig(filename, dpi=300)
        print(f"\nPlot saved successfully as '{filename}'!")
        
        # Show the plot, then close it to prepare for the next loop iteration
        plt.show()
        plt.close()

def main():
    directory = sys.argv[1] if len(sys.argv) > 1 else '.'
    print(f"Scanning directory: {os.path.abspath(directory)}\n")
    
    data = parse_benchmark_files(directory)
    
    if not data or all(not sizes for sizes in data.values()):
        print("No valid benchmark files with throughput data found.")
        sys.exit(1)
        
    averages = calculate_trimmed_averages(data)
    plot_graphs(averages)

if __name__ == '__main__':
    main()
