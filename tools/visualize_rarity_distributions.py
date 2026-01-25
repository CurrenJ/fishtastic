import numpy as np
import matplotlib.pyplot as plt
from scipy.stats import norm

# Define the rarity modifiers from FishtasticFishItem.java
rarity_modifiers = {
    'COMMON': {'meanOffset': 0.0, 'stdDevOffset': 0.0, 'meanMultiplier': 1.0, 'stdDevMultiplier': 1.0},
    'UNCOMMON': {'meanOffset': 5.0, 'stdDevOffset': 0.0, 'meanMultiplier': 1.1, 'stdDevMultiplier': 1.1},
    'RARE': {'meanOffset': 10.0, 'stdDevOffset': 0.0, 'meanMultiplier': 1.2, 'stdDevMultiplier': 1.2},
    'EPIC': {'meanOffset': 20.0, 'stdDevOffset': 0.0, 'meanMultiplier': 1.3, 'stdDevMultiplier': 1.3},
    'LEGENDARY': {'meanOffset': 30.0, 'stdDevOffset': 0.0, 'meanMultiplier': 1.5, 'stdDevMultiplier': 1.5}
}

# Base distribution parameters (from getSizeDefault)
base_mean = 50.0
base_std_dev = 15.0

# Create figure
plt.figure(figsize=(14, 8))

# Define colors for each rarity
colors = {
    'COMMON': '#808080',      # Gray
    'UNCOMMON': '#00FF00',    # Green
    'RARE': '#0070DD',        # Blue
    'EPIC': '#A335EE',        # Purple
    'LEGENDARY': '#FF8000'    # Orange
}

# Generate x values (size range)
x = np.linspace(0, 150, 1000)

# Plot each distribution
for rarity, modifiers in rarity_modifiers.items():
    # Apply modifiers to base distribution
    modified_mean = (base_mean * modifiers['meanMultiplier']) + modifiers['meanOffset']
    modified_std_dev = (base_std_dev * modifiers['stdDevMultiplier']) + modifiers['stdDevOffset']

    # Calculate probability density
    y = norm.pdf(x, modified_mean, modified_std_dev)

    # Plot the distribution
    plt.plot(x, y, label=f'{rarity} (μ={modified_mean:.1f}, σ={modified_std_dev:.1f})',
             color=colors[rarity], linewidth=2)

    # Fill under the curve with transparency
    plt.fill_between(x, y, alpha=0.2, color=colors[rarity])

# Customize the plot
plt.title('Fish Size Distribution by Rarity', fontsize=16, fontweight='bold')
plt.xlabel('Fish Size (cm)', fontsize=12)
plt.ylabel('Probability Density', fontsize=12)
plt.legend(loc='upper right', fontsize=10)
plt.grid(True, alpha=0.3, linestyle='--')
plt.xlim(0, 150)

# Add vertical line for base mean
plt.axvline(x=base_mean, color='black', linestyle='--', alpha=0.5,
            label=f'Base Mean ({base_mean})')

# Tight layout for better spacing
plt.tight_layout()

# Save the plot
output_file = 'rarity_distributions.png'
plt.savefig(output_file, dpi=300, bbox_inches='tight')
print(f"Chart saved as '{output_file}'")

# Display the plot
plt.show()

# Print statistics for each rarity
print("\n" + "="*60)
print("Fish Size Statistics by Rarity")
print("="*60)
for rarity, modifiers in rarity_modifiers.items():
    modified_mean = (base_mean * modifiers['meanMultiplier']) + modifiers['meanOffset']
    modified_std_dev = (base_std_dev * modifiers['stdDevMultiplier']) + modifiers['stdDevOffset']

    # Calculate percentiles
    p25 = norm.ppf(0.25, modified_mean, modified_std_dev)
    p50 = norm.ppf(0.50, modified_mean, modified_std_dev)
    p75 = norm.ppf(0.75, modified_mean, modified_std_dev)
    p95 = norm.ppf(0.95, modified_mean, modified_std_dev)

    print(f"\n{rarity}:")
    print(f"  Mean: {modified_mean:.2f} cm")
    print(f"  Std Dev: {modified_std_dev:.2f} cm")
    print(f"  25th percentile: {p25:.2f} cm")
    print(f"  50th percentile (median): {p50:.2f} cm")
    print(f"  75th percentile: {p75:.2f} cm")
    print(f"  95th percentile: {p95:.2f} cm")
print("="*60)
