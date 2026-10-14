import { Pipe, PipeTransform } from '@angular/core';
import { timePart } from './time';

/**
 * Formats a booking's time range for templates:
 *   {{ booking | timeRange }}  ->  "10:00 – 11:30"
 *
 * A pipe is a reusable value transformer for templates. Standalone pipes are imported directly
 * by the components that use them. Pipes are "pure" by default: Angular only re-runs transform()
 * when the input reference changes, so they are cheap.
 */
@Pipe({ name: 'timeRange', standalone: true })
export class TimeRangePipe implements PipeTransform {
  transform(value: { startTime: string; endTime: string } | null | undefined): string {
    if (!value) {
      return '';
    }
    return `${timePart(value.startTime)} – ${timePart(value.endTime)}`;
  }
}
