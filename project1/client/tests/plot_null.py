import os
import glob
import csv
import matplotlib.pyplot as plt

def main():
    # Find all files matching the null_delay pattern
    files = glob.glob('null_delay_*.csv')
    
    if not files:
        print("No matching files found. Ensure your files start with 'null_delay_'.")
        return

    data = []
    
    # Parse each file
    for filepath in files:
        with open(filepath, 'r') as csvfile:
            reader = csv.DictReader(csvfile)
            for row in reader:
                data.append({
                    'server_type': row['Server Type'].capitalize(),
                    'iterations': row['Iterations Attempted'],
                    'successes': row['Successful Requests'],
                    'avg_delay': float(row['Average Delay (ms)']),
                    'std_dev': float(row['Standard Dev (ms)']),
                    'min_delay': float(row['Min Delay (ms)']),
                    'max_delay': float(row['Max Delay (ms)'])
                })

    if not data:
        print("No valid data found in the CSV files.")
        return

    # Sort data: Single Server first, then Multi-Server
    # (Reverse alphabetical sort makes 'Single' appear before 'Multi')
    data.sort(key=lambda x: x['server_type'], reverse=True)

    # ==========================================
    # GENERATE THE DATA TABLE IMAGE
    # ==========================================
    # Increased width to 15 to ensure all columns fit perfectly. 
    # Height scales dynamically based on how many CSV files it finds.
    fig, ax_table = plt.subplots(figsize=(15, 2.0 + len(data) * 0.6))
    ax_table.axis('off')  # Hide the graph axes entirely
    
    # Title
    plt.title('Null Request-Reply Interaction Latency', fontsize=18, fontweight='bold', pad=20)
    
    cell_text = []
    for d in data:
        cell_text.append([
            d['server_type'],
            d['iterations'],
            d['successes'],
            f"{d['avg_delay']:.3f}",
            f"{d['min_delay']:.3f} / {d['max_delay']:.3f}",
            f"{d['std_dev']:.3f}"
        ])
        
    columns = ('Setup', 'Iterations', 'Successes', 'Avg Delay (ms)', 'Min / Max (ms)', 'Std Dev (ms)')
    
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

    # Leave room at the top for the title and bottom for the text box
    plt.subplots_adjust(top=0.8, bottom=0.2)

    # Add Test Configuration & Disclaimers Text Box
    info_text = (
        f"* Note: The minimum and maximum individual delay outliers have been discarded\n"
        f"from these metrics."
    )
    
    fig.text(0.5, 0.05, info_text, ha="center", fontsize=12, 
              bbox={"facecolor": "#fffbcc", "alpha": 0.8, "pad": 10, "boxstyle": "round,pad=0.6", "edgecolor": "gray"})

    table_filename = "null_latency_table.png"
    fig.savefig(table_filename, dpi=300, bbox_inches='tight')
    print(f"Table saved successfully as '{table_filename}'")

    plt.show()

if __name__ == "__main__":
    main()
