import pandas as pd
import matplotlib.pyplot as plt
import os

# Ensure the CSV exists
if not os.path.exists("results.csv"):
    print("Error: results.csv not found. Run your bash benchmark script first!")
    exit(1)

# Load the data
df = pd.read_csv("results.csv")

# Set up a clean, professional style for the plots
plt.style.use('ggplot')

# ==============================================================================
# Chart 1: The Cache Effect (Network Packets Sent vs. Cache Size)
# Isolates the cache test (Fixed BlockSize=4096, FreshT=5)
# ==============================================================================
cache_data = df[(df['BlockSize'] == 4096) & (df['FreshT'] == 5)].sort_values('CacheBlocks')

if not cache_data.empty:
    plt.figure(figsize=(8, 5))
    plt.plot(cache_data['CacheBlocks'], cache_data['PktsSent'], marker='o', linestyle='-', color='b')
    plt.title('Network Traffic vs. Cache Size (10MB File Read)')
    plt.xlabel('Max Cached Blocks')
    plt.ylabel('Total Packets Sent')
    plt.grid(True)
    plt.tight_layout()
    plt.savefig('chart1_cache_vs_traffic.png')
    print("Generated: chart1_cache_vs_traffic.png")

# ==============================================================================
# Chart 2: Throughput (Speed MB/s vs. Block Size)
# Isolates the baseline test (Cache=0) to show raw network performance
# ==============================================================================
block_data = df[df['CacheBlocks'] == 0].sort_values('BlockSize')

if not block_data.empty:
    plt.figure(figsize=(8, 5))
    # Using a bar chart here looks cleaner for discrete block sizes like 1024, 4096
    plt.bar(block_data['BlockSize'].astype(str), block_data['MBps'], color='seagreen')
    plt.title('Raw Network Throughput vs. Block Size (No Cache)')
    plt.xlabel('Block Size (Bytes)')
    plt.ylabel('Speed (MB/s)')
    plt.grid(axis='y')
    plt.tight_layout()
    plt.savefig('chart2_speed_vs_blocksize.png')
    print("Generated: chart2_speed_vs_blocksize.png")

# ==============================================================================
# Chart 3: The Cache Cliff (Baseline vs. Granular Cache Sizes)
# Visually proves cache thrashing and the 2560-block working set requirement
# ==============================================================================
target_caches = [0, 1000, 2000, 2250, 2500, 2750, 3000]
compare_data = df[(df['BlockSize'] == 4096) & (df['CacheBlocks'].isin(target_caches))].sort_values('CacheBlocks')

if not compare_data.empty:
    plt.figure(figsize=(10, 6))
    
    # DYNAMIC LABELS & COLORS: Only create labels/colors for rows that actually exist
    labels = ['Baseline\n(No Cache)' if x == 0 else str(int(x)) for x in compare_data['CacheBlocks']]
    colors = ['crimson' if x == 0 else 'steelblue' for x in compare_data['CacheBlocks']]
    throughput = compare_data['MBps'].values
    
    bars = plt.bar(labels, throughput, color=colors)
    plt.title('Throughput vs. Cache Size (4096 Block)')
    plt.xlabel('Cache Size (Max Blocks)')
    plt.ylabel('Throughput (MB/s)')
    
    # Add the exact MB/s numbers floating on top of the bars
    for bar in bars:
        yval = bar.get_height()
        offset = max(throughput) * 0.02 
        plt.text(bar.get_x() + bar.get_width()/2, yval + offset, f"{round(yval, 1)}", 
                 ha='center', va='bottom', fontweight='bold', fontsize=10)
    
    # DYNAMIC VERTICAL LINE: Find exactly where 2500 is and draw the line right after it
    cache_list = list(compare_data['CacheBlocks'])
    if 2500 in cache_list and 2750 in cache_list:
        idx_2500 = cache_list.index(2500)
        # x-coordinates for bars are 0, 1, 2... so we draw the line at index + 0.5
        plt.axvline(x=idx_2500 + 0.5, color='orange', linestyle='--', linewidth=2.5, 
                    label='Working Set Limit (2560 Blocks)')
        plt.legend(loc='upper left')

    plt.tight_layout()
    plt.savefig('chart3_cache_thrashing_cliff.png')
    print("Generated: chart3_cache_thrashing_cliff.png")
else:
    print("Could not generate Chart 3: No matching data found.")

print("All charts generated successfully!")