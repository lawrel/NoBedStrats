package voxelloge.bed;

import org.bukkit.metadata.MetadataValueAdapter;
import org.bukkit.plugin.Plugin;


class MetaCounter extends MetadataValueAdapter {
	private volatile int count;

	public MetaCounter(Plugin plugin, int initial) {
		super(plugin);
		this.count = initial;
	}

	@Override
	public void invalidate() {}

	@Override
	public Object value() {
		return this.count;
	}

	@Override
	public int asInt() {
		return this.count;
	}

	public int preIncrement() {
		return ++this.count;
	}

	public int postIncrement() {
		return this.count++;
	}

	public int preDecrement() {
		return --this.count;
	}

	public int postDecrement() {
		return this.count--;
	}
}
