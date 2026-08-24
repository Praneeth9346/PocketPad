from collections import deque
from typing import Any, Callable, Generic, Optional, TypeVar

T = TypeVar('T')


class ObjectPool(Generic[T]):
    """Object pool for reusing objects to reduce garbage collection and heap churn."""
    
    def __init__(self, factory: Callable[[], T], max_size: int = 100):
        self._factory = factory
        self._max_size = max_size
        self._pool: deque = deque()
    
    def acquire(self) -> T:
        """Acquire an object from the pool or create a new one."""
        if self._pool:
            return self._pool.popleft()
        return self._factory()
    
    def release(self, obj: T):
        """Release an object back to the pool."""
        if len(self._pool) < self._max_size:
            if hasattr(obj, 'reset') and callable(getattr(obj, 'reset')):
                obj.reset()
            self._pool.append(obj)
    
    def clear(self):
        """Clear the pool."""
        self._pool.clear()
    
    @property
    def size(self) -> int:
        """Get current pool size."""
        return len(self._pool)
