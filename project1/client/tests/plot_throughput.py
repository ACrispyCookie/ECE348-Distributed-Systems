import os
import glob
import csv
import re
import matplotlib.pyplot as plt

def main():
    # Find all files matching the pattern
    files = glob.glob('throughput_single_*_*w.csv')
    
    if not files:
        print("No matching files found. Ensure your files end with '_Nw.csv' (e.g., '_1w.csv').")
        return

    data = []
    
    # Parse each file
    for filepath in files:
        filename = os.path.basename(filepath)
        match = re.search(r'_(\d+)w\.csv$', filename)
        
        if match:
            workers = int(match.group(1))
            
            with open(filepath, 'r') as csvfile:
                reader = csv.DictReader(csvfile)
                for row in reader:
                    data.append({
                        'workers': workers,
                        'clients': row['Clients'],
                        'req_per_client': row['Requests per Client'],
                        'payload': row['Payload Type'],
                        'throughput': float(row['Throughput (req/sec)']),
                        'avg_delay': float(row['Average Req Delay (ms)']),
                        'std_dev': float(row['Standard Dev (ms)']),
                        'min_delay': float(row['Min Req Delay (ms)']),
                        'max_delay': float(row['Max Req Delay (ms)'])
                    })

    if not data:
        print("No valid data found in the CSV files.")
        return

    # Sort data points by the number of workers
    data.sort(key=lambda x: x['workers'])

    workers = [d['workers'] for d in data]
    throughput = [d['throughput'] for d in data]

    # ==========================================
    # IMAGE 1: THE PLOT & NOTES
    # ==========================================
    fig1, ax_plot = plt.subplots(figsize=(10, 7))
    
    ax_plot.plot(workers, throughput, marker='o', linestyle='-', color='#1f77b4', linewidth=2.5, markersize=5)
    
    # Add data labels directly on the points
    for w, t in zip(workers, throughput):
        ax_plot.annotate(f"{t:.1f}", (w, t), textcoords="offset points", xytext=(12, -12), ha='center', fontsize=12)
    
    ax_plot.set_xlabel('Number of Workers', fontsize=12, fontweight='bold')
    ax_plot.set_ylabel('Throughput (Requests / sec)', fontsize=12, fontweight='bold')
    ax_plot.set_title('Server Throughput vs. Worker Threads', fontsize=16, fontweight='bold', pad=15)
    ax_plot.grid(True, linestyle='--', alpha=0.7)
    ax_plot.set_xticks(workers)
    
    # Reserve space at the bottom for the text box
    fig1.subplots_adjust(bottom=0.2)

    # Add Test Configuration & Disclaimers Text Box
    clients = data[0]['clients']
    req_per_client = data[0]['req_per_client']
    payload = data[0]['payload']

    info_text = (
        f"Test Configuration: {clients} Clients | {req_per_client} Requests/Client | Payload: {payload}\n"
        f"* Note: Fastest and slowest client times, as well as min/max delay outliers, "
        f"have been discarded from these calculations."
    )
    
    fig1.text(0.5, 0.05, info_text, ha="center", fontsize=11, 
              bbox={"facecolor": "#fffbcc", "alpha": 0.8, "pad": 10, "boxstyle": "round,pad=0.6", "edgecolor": "gray"})

    plot_filename = "throughput_scaling_plot.png"
    fig1.savefig(plot_filename, dpi=300, bbox_inches='tight')
    print(f"Plot saved successfully as '{plot_filename}'")

    # ==========================================
    # IMAGE 2: THE DATA TABLE
    # ==========================================
    # Create a new figure just for the table. Height is dynamic based on row count.
    fig2, ax_table = plt.subplots(figsize=(10, 1 + len(data) * 0.5))
    ax_table.axis('off')  # Hide axes
    
    cell_text = []
    for d in data:
        cell_text.append([
            f"{d['workers']}",
            f"{d['avg_delay']:.2f}",
            f"{d['min_delay']:.2f} / {d['max_delay']:.2f}",
            f"{d['std_dev']:.2f}"
        ])
        
    columns = ('Workers', 'Avg Delay (ms)', 'Min / Max (ms)', 'Std Dev (ms)')
    
    the_table = ax_table.table(cellText=cell_text,
                               colLabels=columns,
                               loc='center',
                               cellLoc='center')
    
    the_table.auto_set_font_size(False)
    the_table.set_fontsize(14)
    the_table.scale(1, 2.5) # Scale rows to look spacious and clean

    # Make the column headers bold and distinct
    for (row, col), cell in the_table.get_celld().items():
        if row == 0:
            cell.set_text_props(fontweight='bold')
            cell.set_facecolor('#f0f0f0')

    table_filename = "throughput_scaling_table.png"
    fig2.savefig(table_filename, dpi=300, bbox_inches='tight')
    print(f"Table saved successfully as '{table_filename}'")

    # Show both figures
    plt.show()

if __name__ == "__main__":
    main()
