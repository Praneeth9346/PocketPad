import asyncio
import functools
import logging
from typing import Any, Callable

from .exceptions import PocketPadError


def handle_errors(logger: logging.Logger):
    """Decorator for handling errors in sync and async functions."""
    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        async def async_wrapper(*args, **kwargs) -> Any:
            try:
                return await func(*args, **kwargs)
            except PocketPadError as e:
                logger.error("%s: %s", func.__name__, e)
                raise
            except Exception as e:
                logger.exception("Unexpected error in %s: %s", func.__name__, e)
                raise PocketPadError(f"Unexpected error in {func.__name__}: {e}") from e
        
        @functools.wraps(func)
        def sync_wrapper(*args, **kwargs) -> Any:
            try:
                return func(*args, **kwargs)
            except PocketPadError as e:
                logger.error("%s: %s", func.__name__, e)
                raise
            except Exception as e:
                logger.exception("Unexpected error in %s: %s", func.__name__, e)
                raise PocketPadError(f"Unexpected error in {func.__name__}: {e}") from e
        
        if asyncio.iscoroutinefunction(func):
            return async_wrapper
        return sync_wrapper
    
    return decorator
