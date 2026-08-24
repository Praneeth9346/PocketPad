import gc
import logging
import time
from typing import Any, Dict, List
import psutil


class PerformanceMonitor:
    """Monitor and optimize application performance."""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
        self._metrics: Dict[str, List[float]] = {}
        self._start_time = time.time()
    
    def record_metric(self, name: str, value: float):
        """Record a performance metric."""
        if name not in self._metrics:
            self._metrics[name] = []
        self._metrics[name].append(float(value))
        
        # Keep only last 100 measurements
        if len(self._metrics[name]) > 100:
            self._metrics[name] = self._metrics[name][-100:]
    
    def get_average_metric(self, name: str) -> float:
        """Get average value of a metric."""
        if name not in self._metrics or not self._metrics[name]:
            return 0.0
        return sum(self._metrics[name]) / len(self._metrics[name])
    
    def get_memory_usage(self) -> Dict[str, float]:
        """Get current memory usage in MB."""
        try:
            process = psutil.Process()
            memory_info = process.memory_info()
            
            return {
                'rss': memory_info.rss / (1024 * 1024),  # MB
                'vms': memory_info.vms / (1024 * 1024),  # MB
                'percent': process.memory_percent()
            }
        except Exception:
            return {'rss': 0.0, 'vms': 0.0, 'percent': 0.0}
    
    def get_cpu_usage(self) -> float:
        """Get current CPU usage percent."""
        try:
            return psutil.cpu_percent(interval=0.1)
        except Exception:
            return 0.0
    
    def optimize_memory(self):
        """Optimize memory usage and force garbage collection."""
        collected = gc.collect()
        
        # Clear metrics if too many
        for name in list(self._metrics.keys()):
            if len(self._metrics[name]) > 1000:
                self._metrics[name] = self._metrics[name][-100:]
        
        self.logger.debug("Memory optimized. GC collected: %d", collected)
    
    def get_uptime(self) -> float:
        """Get application uptime in seconds."""
        return time.time() - self._start_time
    
    def get_stats(self) -> Dict[str, Any]:
        """Get performance statistics."""
        return {
            'uptime': self.get_uptime(),
            'memory': self.get_memory_usage(),
            'cpu_usage': self.get_cpu_usage(),
            'metrics': {
                name: {
                    'average': self.get_average_metric(name),
                    'count': len(values)
                }
                for name, values in self._metrics.items()
            }
        }


performance_monitor = PerformanceMonitor()
