package g3deditor.jogl;

import g3deditor.geo.GeoBlock;
import g3deditor.geo.GeoCell;
import g3deditor.geo.GeoEngine;
import g3deditor.geo.GeoRegion;
import g3deditor.geo.blocks.GeoBlockFlat;
import g3deditor.util.TaskExecutor;
import g3deditor.util.Util;

import java.util.Comparator;

import javax.media.opengl.GL2;

public final class GLRenderSelector
{
	private final GLDisplay _display;
	private final GLSubRenderSelectorComparator _glSubRenderSelectorComparator;
	private final GeoCellComparator _cellComparator;
	private final TaskExecutor _taskExecutor;
	
	private float[][] _frustum;
	
	private GLSubRenderSelector[] _geoBlocks;
	private GLSubRenderSelector[] _geoBlocksSorted;
	private int _geoBlocksSize;
	
	private int _camBlockX;
	private int _camBlockY;
	
	public GLRenderSelector(final GLDisplay display)
	{
		_display = display;
		_glSubRenderSelectorComparator = new GLSubRenderSelectorComparator();
		_cellComparator = new GeoCellComparator();
		_geoBlocks = new GLSubRenderSelector[0];
		_geoBlocksSorted = new GLSubRenderSelector[0];
		_taskExecutor = new TaskExecutor(Runtime.getRuntime().availableProcessors());
	}
	
	public final void init()
	{
		_taskExecutor.init();
	}
	
	public final void dispose()
	{
		_taskExecutor.dispose();
	}
	
	public final GLDisplay getDisplay()
	{
		return _display;
	}
	
	public final GeoCellComparator getGeoCellComparator()
	{
		return _cellComparator;
	}
	
	public final void select(final GL2 gl, final GLCamera camera)
	{
		if (camera.positionXZChanged())
		{
			final int geoX = (int) camera.getX();
			final int geoY = (int) camera.getZ();
			final int camBlockX = GeoEngine.getBlockXY(geoX);
			final int camBlockY = GeoEngine.getBlockXY(geoY);
			
			if (camBlockX != _camBlockX || camBlockY != _camBlockY)
			{
				_camBlockX = camBlockX;
				_camBlockY = camBlockY;
				
				final GeoRegion region = GeoEngine.getInstance().getActiveRegion();
				if (region == null)
					throw new NullPointerException("Fuuu");
				
				final int range = 10;
				final int minBlockX = Math.max(camBlockX - range, 0);
				final int maxBlockX = Math.min(camBlockX + range, GeoEngine.GEO_REGION_SIZE - 1);
				final int minBlockY = Math.max(camBlockY - range, 0);
				final int maxBlockY = Math.min(camBlockY + range, GeoEngine.GEO_REGION_SIZE - 1);
				
				final int requiredSize = (range * 2 + 1) * (range * 2 + 1);
				if (_geoBlocks.length != requiredSize)
				{
					_geoBlocks = new GLSubRenderSelector[requiredSize];
					_geoBlocksSorted = new GLSubRenderSelector[requiredSize];
					for (int i = requiredSize; i-- > 0;)
					{
						_geoBlocks[i] = new GLSubRenderSelector();
					}
				}
				
				_geoBlocksSize = 0;
				for (int x = minBlockX, y; x < maxBlockX; x++)
				{
					for (y = minBlockY; y < maxBlockY; y++)
					{
						_geoBlocks[_geoBlocksSize++].setGeoBlock(region.getBlockByBlockXY(x, y));
					}
				}
			}
		}
		
		if (camera.positionXZChanged() || camera.positionYChanged() || camera.rotationChanged())
		{
			_frustum = camera.getFrustum(gl);
			_taskExecutor.execute(_geoBlocks, _geoBlocksSize);
			System.arraycopy(_geoBlocks, 0, _geoBlocksSorted, 0, _geoBlocksSize);
			Util.mergeSort(_geoBlocks, _geoBlocksSorted, _geoBlocksSize, _glSubRenderSelectorComparator);
		}
	}
	
	public final int getElementsToRender()
	{
		return _geoBlocksSize;
	}
	
	public final GLSubRenderSelector getElementToRender(final int index)
	{
		return _geoBlocksSorted[index];
	}
	
	public final boolean isVisible(final GeoBlock block)
	{
		if (block instanceof GeoBlockFlat) // flat block`s cell get checked
			return true;
		
		final float x1 = block.getGeoX();
		final float x2 = x1 + 8f;
		final float y1 = block.getMinHeight() / 16f;
		final float y2 = block.getMaxHeight() / 16f;
		final float z1 = block.getGeoY();
		final float z2 = z1 + 8f;
		
		float[] plane;
		float p, px1, px2, py1, py2, pz1, pz2;
		for (int i = 6; i-- > 0;)
		{
			plane = _frustum[i];
			p = plane[0];
			px1 = p * x1;
			px2 = p * x2;
			p = plane[1];
			py1 = p * y1;
			py2 = p * y2;
			p = plane[2];
			pz1 = p * z1;
			pz2 = p * z2;
			p = plane[3];
			
			if (px1 + py1 + pz1 + p <= 0f &&
					px2 + py1 + pz1 + p <= 0f &&
					px1 + py2 + pz1 + p <= 0f &&
					px2 + py2 + pz1 + p <= 0f &&
					px1 + py1 + pz2 + p <= 0f &&
					px2 + py1 + pz2 + p <= 0f &&
					px1 + py2 + pz2 + p <= 0f &&
					px2 + py2 + pz2 + p <= 0f)
				return false;
		}
		return true;
	}
	
	public final boolean isVisible(final GeoCell cell)
	{
		final float x;
		final float y;
		final float z;
		final float rad;
		
		if (cell.isBig())
		{
			x = cell.getRenderX() + 3.5f;
			y = cell.getRenderY() - 0.1f;
			z = cell.getRenderZ() + 3.5f;
			rad = 5f;
		}
		else
		{
			x = cell.getRenderX() + 0.5f;
			y = cell.getRenderY() - 0.1f;
			z = cell.getRenderZ() + 0.5f;
			rad = 1f;
		}
		
		float[] plane;
		for (int i = 6; i-- > 0;)
		{
			plane = _frustum[i];
			if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] <= -rad)
				return false;
		}
		return true;
	}
	
	public final class GLSubRenderSelector implements Runnable
	{
		private GeoBlock _block;
		
		private GeoCell[] _culledCells;
		private GeoCell[] _sortedCells;
		private int _count;
		
		public GLSubRenderSelector()
		{
			_culledCells = new GeoCell[0];
			_sortedCells = new GeoCell[0];
		}
		
		public final void setGeoBlock(final GeoBlock block)
		{
			_block = block;
		}
		
		public final GeoBlock getGeoBlock()
		{
			return _block;
		}
		
		public final int getElementsToRender()
		{
			return _count;
		}
		
		public final GeoCell getElementToRender(final int index)
		{
			return _sortedCells[index];
		}
		
		@Override
		public final void run()
		{
			_count = 0;
			if (!GLRenderSelector.this.isVisible(_block))
				return;
			
			final GeoCell[] cells = _block.getCells();
			if (_culledCells.length < cells.length)
			{
				_culledCells = new GeoCell[cells.length];
				_sortedCells = new GeoCell[cells.length];
			}
			
			for (final GeoCell cell : cells)
			{
				if (GLRenderSelector.this.isVisible(cell))
					_culledCells[_count++] = cell;
			}
			
			System.arraycopy(_culledCells, 0, _sortedCells, 0, _count);
			Util.mergeSort(_culledCells, _sortedCells, _count, GLRenderSelector.this.getGeoCellComparator());
		}
	}
	
	private final class GLSubRenderSelectorComparator implements Comparator<GLSubRenderSelector>
	{
		public GLSubRenderSelectorComparator()
		{
			
		}
		
		/**
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public final int compare(final GLSubRenderSelector o1, final GLSubRenderSelector o2)
		{
			final GLCamera camera = getDisplay().getCamera();
			final double dx1 = camera.getX() - o1.getGeoBlock().getGeoX();
			final double dy1 = camera.getZ() - o1.getGeoBlock().getGeoY();
			final double dx2 = camera.getX() - o2.getGeoBlock().getGeoX();
			final double dy2 = camera.getZ() - o2.getGeoBlock().getGeoY();
			return (int) ((dx1 * dx1 + dy1 * dy1) - (dx2 * dx2 + dy2 * dy2));
		}
	}
	
	private final class GeoCellComparator implements Comparator<GeoCell>
	{
		public GeoCellComparator()
		{
			
		}
		
		/**
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public final int compare(final GeoCell o1, final GeoCell o2)
		{
			final GLCamera camera = getDisplay().getCamera();
			final double dx1 = camera.getX() - o1.getGeoX();
			final double dy1 = camera.getZ() - o1.getGeoY();
			final double dz1 = camera.getY() - o1.getHeight() / 16f;
			final double dx2 = camera.getX() - o2.getGeoX();
			final double dy2 = camera.getZ() - o2.getGeoY();
			final double dz2 = camera.getY() - o2.getHeight() / 16f;
			return (int) ((dx1 * dx1 + dy1 * dy1 + dz1 * dz1) - (dx2 * dx2 + dy2 * dy2 + dz2 * dz2));
		}
	}
}